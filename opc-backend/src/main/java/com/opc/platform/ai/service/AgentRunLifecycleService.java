package com.opc.platform.ai.service;

import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.entity.AiAgentProviderCall;
import com.opc.platform.ai.mapper.AiAgentProviderCallMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.ai.provider.AiRuntimeSnapshot;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AgentRunLifecycleService {

    private static final Duration FINALIZATION_RESERVE = Duration.ofSeconds(8);
    private static final Duration MIN_PROVIDER_WINDOW = Duration.ofSeconds(1);

    private final AiAnalysisRunMapper runMapper;
    private final AiClient aiClient;
    private final AiRuntimeSettingsProvider settingsProvider;
    private final AiAgentProviderCallMapper providerCallMapper;
    private final AgentProviderSettlementService settlementService;
    private final ScheduledExecutorService leaseScheduler;

    @Autowired
    public AgentRunLifecycleService(
            AiAnalysisRunMapper runMapper,
            AiClient aiClient,
            AiRuntimeSettingsProvider settingsProvider,
            AiAgentProviderCallMapper providerCallMapper,
            AgentProviderSettlementService settlementService,
            @Qualifier("agentLeaseScheduler") ScheduledExecutorService leaseScheduler
    ) {
        this.runMapper = runMapper;
        this.aiClient = aiClient;
        this.settingsProvider = settingsProvider;
        this.providerCallMapper = providerCallMapper;
        this.settlementService = settlementService;
        this.leaseScheduler = leaseScheduler;
    }

    public AgentRunLifecycleService(
            AiAnalysisRunMapper runMapper,
            AiClient aiClient,
            AiRuntimeSettingsProvider settingsProvider,
            AiAgentProviderCallMapper providerCallMapper
    ) {
        this(runMapper, aiClient, settingsProvider, providerCallMapper,
                providerCallMapper == null ? null : new AgentProviderSettlementService(runMapper, providerCallMapper),
                null);
    }

    /** Compatibility constructor for integration fixtures without a lease scheduler. */
    public AgentRunLifecycleService(
            AiAnalysisRunMapper runMapper,
            AiClient aiClient,
            AiRuntimeSettingsProvider settingsProvider,
            AiAgentProviderCallMapper providerCallMapper,
            AgentProviderSettlementService settlementService
    ) {
        this(runMapper, aiClient, settingsProvider, providerCallMapper, settlementService, null);
    }

    public AgentRunLifecycleService(
            AiAnalysisRunMapper runMapper,
            AiClient aiClient,
            AiRuntimeSettingsProvider settingsProvider
    ) {
        this(runMapper, aiClient, settingsProvider, null, null, null);
    }

    public AgentRunLease begin(
            AuthenticatedUser user,
            Long sessionId,
            Long userMessageId,
            String idempotencyKey,
            AgentRuntimeConfig config
    ) {
        runMapper.failExpiredRunning(LocalDateTime.now());
        return reserve(user, sessionId, userMessageId, idempotencyKey, config, "running",
                new AgentSubmissionIdentity("message", null, null, 0L));
    }

    public AiAnalysisRun enqueue(
            AuthenticatedUser user,
            Long sessionId,
            Long userMessageId,
            String idempotencyKey,
            AgentRuntimeConfig config
    ) {
        return enqueue(user, sessionId, userMessageId, idempotencyKey, config,
                new AgentSubmissionIdentity("message", null, null, 0L));
    }

    public AiAnalysisRun enqueue(
            AuthenticatedUser user,
            Long sessionId,
            Long userMessageId,
            String idempotencyKey,
            AgentRuntimeConfig config,
            AgentSubmissionIdentity identity
    ) {
        return reserve(user, sessionId, userMessageId, idempotencyKey, config, "received", identity).run();
    }

    public AgentRunLease resume(AiAnalysisRun run, AgentRuntimeConfig config) {
        if (run == null || !"running".equals(run.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent run is not leased");
        }
        AiRuntimeSnapshot snapshot = settingsProvider.snapshot();
        AiRuntimeSettings runtime = snapshot.settings();
        AiProviderDescriptor descriptor = aiClient.descriptor(runtime);
        if (!descriptor.available()
                || !descriptor.provider().equals(run.getProvider())
                || !descriptor.model().equals(run.getModelId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent provider configuration changed before execution");
        }
        LocalDateTime now = LocalDateTime.now();
        if (run.getDeadlineAt() != null && !run.getDeadlineAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent run deadline expired before execution");
        }
        Duration leaseWindow = leaseWindow(run, config);
        LocalDateTime proposedExpiry = capLeaseExpiry(now, leaseWindow, run.getDeadlineAt());
        int renewed = run.getExecutionAttempts() != null && run.getExecutionAttempts() > 0
                ? runMapper.renewAgentLeaseFenced(
                        run.getId(), run.getLeaseOwner(), run.getExecutionAttempts(), now, proposedExpiry)
                : runMapper.renewAgentLease(run.getId(), run.getLeaseOwner(), now, proposedExpiry);
        if (renewed != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent run lease expired before execution");
        }
        run.setHeartbeatAt(now);
        run.setLeaseExpiresAt(proposedExpiry);
        int existingProviderCalls = providerCallMapper == null ? 0
                : Math.max(0, providerCallMapper.selectMaxRoundNo(run.getId()));
        return new AgentRunLease(run, runtime, descriptor, config, existingProviderCalls);
    }

    private AgentRunLease reserve(
            AuthenticatedUser user,
            Long sessionId,
            Long userMessageId,
            String idempotencyKey,
            AgentRuntimeConfig config,
            String initialStatus,
            AgentSubmissionIdentity identity
    ) {
        if (!config.enabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Agent Runtime 尚未启用");
        }
        AiRuntimeSnapshot snapshot = settingsProvider.snapshot();
        AiRuntimeSettings runtime = snapshot.settings();
        AiProviderDescriptor descriptor = aiClient.descriptor(runtime);
        if (!descriptor.available()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "智能体模型尚未配置或未启用");
        }
        LocalDateTime now = LocalDateTime.now();
        AiAnalysisRun run = new AiAnalysisRun();
        run.setUserId(user.userId());
        run.setTaskType("agent_research");
        run.setSessionId(sessionId);
        run.setUserMessageId(userMessageId);
        run.setIdempotencyKey(idempotencyKey);
        run.setSubmissionKind(identity == null || identity.kind() == null ? "message" : identity.kind());
        run.setRequestedIntent(identity == null
                ? "auto" : ResearchExecutionRequirements.normalizeIntent(identity.requestedIntent()));
        run.setRequestContentHash(identity == null ? null : identity.contentHash());
        run.setStartProfileHash(identity == null ? null : identity.profileHash());
        run.setSessionContentGeneration(identity == null ? 0L : identity.sessionContentGeneration());
        AgentAnalyticsSnapshotBinding analyticsSnapshot = identity == null ? null : identity.analyticsSnapshot();
        run.setAnalyticsSnapshotId(analyticsSnapshot == null ? null : analyticsSnapshot.snapshotId());
        run.setAnalyticsMetricId(analyticsSnapshot == null ? null : analyticsSnapshot.metricId());
        run.setAnalyticsDataVersion(analyticsSnapshot == null ? null : analyticsSnapshot.dataVersion());
        run.setAnalyticsFiltersJson(analyticsSnapshot == null ? null : analyticsSnapshot.filtersJson());
        run.setAnalyticsSnapshotJson(analyticsSnapshot == null ? null : analyticsSnapshot.snapshotJson());
        run.setStatus(initialStatus);
        run.setProvider(descriptor.provider());
        run.setModelId(descriptor.model());
        run.setPromptVersion(AgentResearchContract.PROMPT_VERSION);
        run.setEvidenceHash(hash(sessionId + ":" + userMessageId + ":" + idempotencyKey));
        run.setPromptTokens(0);
        run.setCompletionTokens(0);
        run.setTotalTokens(0);
        run.setReservedTokens((long) config.maxTokens());
        run.setStartedAt("running".equals(initialStatus) ? now : null);
        run.setDeadlineAt(now.plus(config.timeout()));
        run.setHeartbeatAt("running".equals(initialStatus) ? now : null);
        run.setCurrentStage("received");
        run.setVisibleProgress("正在分析需求");
        run.setStepCount(0);
        run.setToolCallCount(0);
        try {
            if (runMapper.reserve(run, snapshot.dailyTokenQuota(), config.maxTokens()) != 1) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "今日智能体 Token 配额不足");
            }
        } catch (DuplicateKeyException exception) {
            AiAnalysisRun existing = runMapper.findAgentByIdempotency(user.userId(), idempotencyKey);
            if (existing != null) {
                return new AgentRunLease(existing, runtime, descriptor, config);
            }
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "已有研究运行正在进行");
        }
        return new AgentRunLease(run, runtime, descriptor, config);
    }

    public AiProviderResponse invoke(AgentRunLease lease, AiProviderRequest request) {
        LocalDateTime dispatchedAt = LocalDateTime.now();
        AiRuntimeSettings boundedRuntime = boundedRuntime(lease, dispatchedAt);
        if (providerCallMapper != null) {
            return invokeAudited(lease, request, boundedRuntime, dispatchedAt);
        }
        AiProviderResponse response = aiClient.generate(request, boundedRuntime);
        int total = Math.max(Math.max(0, response.totalTokens()),
                Math.max(0, response.promptTokens()) + Math.max(0, response.completionTokens()));
        LocalDateTime now = LocalDateTime.now();
        int updated = fenced(lease)
                ? runMapper.recordAgentUsageFenced(
                        lease.run().getId(), lease.leaseOwner(), lease.executionAttempt(),
                        Math.max(0, response.promptTokens()), Math.max(0, response.completionTokens()), total,
                        Math.max(0, response.latencyMs()), response.requestId(), response.finishReason(), now)
                : runMapper.recordAgentUsage(
                        lease.run().getId(), Math.max(0, response.promptTokens()), Math.max(0, response.completionTokens()),
                        total, Math.max(0, response.latencyMs()), response.requestId(), response.finishReason(), now);
        if (updated != 1) {
            lease.markLeaseLost();
            if (lease.leaseLost()) throw leaseLost(lease, now);
            throw new BusinessException(ErrorCode.CONFLICT, "研究运行已取消、失败或过期，迟到结果已丢弃");
        }
        lease.add(response);
        return response;
    }

    private AiProviderResponse invokeAudited(
            AgentRunLease lease,
            AiProviderRequest request,
            AiRuntimeSettings boundedRuntime,
            LocalDateTime dispatchedAt
    ) {
        int marked = fenced(lease)
                ? runMapper.markAgentProviderDispatchedFenced(
                        lease.run().getId(), lease.leaseOwner(), lease.executionAttempt(), dispatchedAt)
                : runMapper.markAgentProviderDispatched(lease.run().getId(), dispatchedAt);
        if (marked != 1) {
            lease.markLeaseLost();
            if (lease.leaseLost()) throw leaseLost(lease, dispatchedAt);
            throw new BusinessException(ErrorCode.CONFLICT, "Agent run was cancelled before provider dispatch");
        }
        AiAgentProviderCall call = new AiAgentProviderCall();
        call.setAnalysisRunId(lease.run().getId());
        call.setRoundNo(lease.nextProviderCall());
        call.setInternalRequestId(UUID.randomUUID().toString());
        call.setSettlementStatus("provider_dispatched");
        call.setReservedTokens(Math.max(0, lease.config().maxTokens() - lease.totalTokens()));
        call.setPromptTokens(0);
        call.setCompletionTokens(0);
        call.setTotalTokens(0);
        call.setLatencyMs(0L);
        call.setDispatchedAt(dispatchedAt);
        int inserted = fenced(lease)
                ? providerCallMapper.insertGuardedFenced(
                        call, lease.leaseOwner(), lease.executionAttempt(), dispatchedAt)
                : providerCallMapper.insert(call);
        if (inserted != 1) {
            lease.markLeaseLost();
            throw leaseLost(lease, dispatchedAt);
        }

        AiProviderResponse response = aiClient.generate(request, boundedRuntime);
        boolean settled = fenced(lease)
                ? settlementService.settleActual(
                        call.getId(), response, lease.leaseOwner(), lease.executionAttempt())
                : settlementService.settleActual(call.getId(), response);
        if (settled) {
            lease.add(response);
        } else if (fenced(lease)) {
            lease.markLeaseLost();
            if (lease.leaseLost()) throw leaseLost(lease, LocalDateTime.now());
            throw new BusinessException(ErrorCode.CONFLICT, "Agent run lease was lost before provider response settlement");
        }
        return response;
    }

    public boolean settleLateUsage(Long providerCallId, AiProviderResponse response) {
        return settlementService != null && settlementService.settleLateUsage(providerCallId, response);
    }

    public void updateStage(AgentRunLease lease, String stage, int stepCount, int toolCallCount) {
        LocalDateTime now = LocalDateTime.now();
        int updated = fenced(lease)
                ? runMapper.updateAgentStageFenced(
                        lease.run().getId(), lease.leaseOwner(), lease.executionAttempt(), stage,
                        visibleProgress(stage), stepCount, toolCallCount, now)
                : runMapper.updateAgentStage(
                        lease.run().getId(), stage, visibleProgress(stage), stepCount, toolCallCount, now);
        if (updated != 1) {
            lease.markLeaseLost();
            throw new BusinessException(ErrorCode.CONFLICT, "研究运行已取消、失败或过期");
        }
        lease.updateProgress(stepCount, toolCallCount);
    }

    public void complete(AgentRunLease lease, AgentOrchestratorOutcome outcome, String resultJson) {
        LocalDateTime completedAt = LocalDateTime.now();
        int settled = fenced(lease)
                ? runMapper.settleAgentCompletedFenced(
                        lease.run().getId(), lease.leaseOwner(), lease.executionAttempt(), outcome.status(),
                        outcome.promptTokens(), outcome.completionTokens(), outcome.totalTokens(), outcome.latencyMs(),
                        outcome.requestId(), outcome.finishReason(), outcome.modelRounds(), outcome.toolCallCount(),
                        resultJson, outcome.diagnosticCode(), completedAt)
                : runMapper.settleAgentCompleted(
                        lease.run().getId(), outcome.status(), outcome.promptTokens(), outcome.completionTokens(),
                        outcome.totalTokens(), outcome.latencyMs(), outcome.requestId(), outcome.finishReason(),
                        outcome.modelRounds(), outcome.toolCallCount(), resultJson, outcome.diagnosticCode(), completedAt);
        if (settled != 1) {
            lease.markLeaseLost();
            throw new BusinessException(ErrorCode.CONFLICT, "研究运行已取消、失败或过期，完成结果未写入");
        }
    }

    public void fail(AgentRunLease lease, String status, ErrorCode errorCode, String diagnosticCode) {
        LocalDateTime now = LocalDateTime.now();
        String terminalStatus = fenced(lease) && lease.deadlineReached(now) ? "expired" : status;
        String terminalDiagnostic = "expired".equals(terminalStatus) ? "AGENT_TIMEOUT" : diagnosticCode;
        if (settlementService != null) {
            boolean settled = fenced(lease)
                    ? settlementService.settleFailure(
                            lease.run().getId(), terminalStatus, errorCode.name(), terminalDiagnostic,
                            lease.modelRounds(), lease.toolCallCount(),
                            lease.leaseOwner(), lease.executionAttempt())
                    : settleLegacyFailure(lease, terminalStatus, errorCode, terminalDiagnostic);
            if (!settled) lease.markLeaseLost();
            return;
        }
        if (fenced(lease)) {
            int settled = "expired".equals(terminalStatus)
                    ? runMapper.expireAgentRunFenced(
                            lease.run().getId(), lease.leaseOwner(), lease.executionAttempt(), now)
                    : runMapper.settleAgentFailedFenced(
                            lease.run().getId(), lease.leaseOwner(), lease.executionAttempt(), terminalStatus,
                            "研究运行未完成", errorCode.name(), diagnosticCode,
                            lease.modelRounds(), lease.toolCallCount(), now);
            if (settled != 1) lease.markLeaseLost();
            return;
        }
        LocalDateTime failedAt = LocalDateTime.now();
        int estimatedCalls = 0;
        if (providerCallMapper != null) {
            estimatedCalls = providerCallMapper.markDispatchedEstimated(lease.run().getId(), failedAt);
        }
        runMapper.settleAgentFailed(
                lease.run().getId(), status, "研究运行未完成", errorCode.name(), diagnosticCode,
                lease.modelRounds(), lease.toolCallCount(), failedAt);
        if (estimatedCalls > 0) {
            runMapper.reconcileAgentProviderUsage(lease.run().getId(), failedAt);
        }
    }

    private boolean settleLegacyFailure(
            AgentRunLease lease,
            String status,
            ErrorCode errorCode,
            String diagnosticCode
    ) {
        settlementService.settleFailure(
                lease.run().getId(), status, errorCode.name(), diagnosticCode,
                lease.modelRounds(), lease.toolCallCount());
        return true;
    }

    public LeaseHeartbeat startLeaseHeartbeat(AgentRunLease lease) {
        if (!fenced(lease) || leaseScheduler == null) return LeaseHeartbeat.NOOP;
        Duration window = lease.leaseWindow();
        long intervalMillis = Math.max(250L, Math.min(10_000L, Math.max(1L, window.toMillis() / 3)));
        ScheduledFuture<?> future = leaseScheduler.scheduleAtFixedRate(() -> renewLease(lease),
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    public boolean renewLease(AgentRunLease lease) {
        if (!fenced(lease)) return true;
        LocalDateTime now = LocalDateTime.now();
        if (lease.deadlineReached(now)) {
            lease.markLeaseLost();
            return false;
        }
        LocalDateTime leaseExpiresAt = capLeaseExpiry(now, lease.leaseWindow(), lease.run().getDeadlineAt());
        int renewed = runMapper.renewAgentLeaseFenced(
                lease.run().getId(), lease.leaseOwner(), lease.executionAttempt(), now, leaseExpiresAt);
        if (renewed != 1) {
            lease.markLeaseLost();
            return false;
        }
        lease.run().setHeartbeatAt(now);
        lease.run().setLeaseExpiresAt(leaseExpiresAt);
        return true;
    }

    private AiRuntimeSettings boundedRuntime(AgentRunLease lease, LocalDateTime now) {
        if (lease.run().getDeadlineAt() != null && !lease.run().getDeadlineAt().isAfter(now)) {
            lease.markLeaseLost();
            throw new AgentOrchestratorException(
                    ErrorCode.UPSTREAM_ERROR, "AGENT_TIMEOUT", "Agent run deadline elapsed before provider dispatch");
        }
        if (fenced(lease) && !lease.isCurrentAt(now)) {
            lease.markLeaseLost();
            throw new AgentOrchestratorException(
                    ErrorCode.CONFLICT, "AGENT_LEASE_LOST", "Agent run lease is no longer current");
        }
        Duration timeout = lease.runtime().timeout() == null ? Duration.ofSeconds(1) : lease.runtime().timeout();
        if (lease.run().getDeadlineAt() != null) {
            Duration remaining = Duration.between(now, lease.run().getDeadlineAt());
            if (remaining.isZero() || remaining.isNegative()) {
                lease.markLeaseLost();
                throw new AgentOrchestratorException(
                        ErrorCode.UPSTREAM_ERROR, "AGENT_TIMEOUT", "Agent run deadline elapsed before provider dispatch");
            }
            Duration providerWindow = remaining.minus(FINALIZATION_RESERVE);
            if (providerWindow.compareTo(MIN_PROVIDER_WINDOW) < 0) {
                throw new AgentOrchestratorException(
                        ErrorCode.UPSTREAM_ERROR, AgentResearchContract.AGENT_DEADLINE_FALLBACK,
                        "Agent run has insufficient time for another provider request and final settlement");
            }
            if (providerWindow.compareTo(timeout) < 0) timeout = providerWindow;
        }
        return new AiRuntimeSettings(
                lease.runtime().provider(), lease.runtime().apiFormat(), lease.runtime().apiBaseUrl(),
                lease.runtime().model(), lease.runtime().apiKey(), lease.runtime().temperature(),
                lease.runtime().maxOutputTokens(), timeout, lease.runtime().retryCount(), lease.runtime().enabled());
    }

    private Duration leaseWindow(AiAnalysisRun run, AgentRuntimeConfig config) {
        if (run.getHeartbeatAt() != null && run.getLeaseExpiresAt() != null
                && run.getLeaseExpiresAt().isAfter(run.getHeartbeatAt())) {
            return Duration.between(run.getHeartbeatAt(), run.getLeaseExpiresAt());
        }
        return config == null || config.timeout() == null ? Duration.ofSeconds(45) : config.timeout();
    }

    private LocalDateTime capLeaseExpiry(
            LocalDateTime now,
            Duration requestedWindow,
            LocalDateTime deadlineAt
    ) {
        LocalDateTime requested = now.plus(requestedWindow.isNegative() || requestedWindow.isZero()
                ? Duration.ofSeconds(1) : requestedWindow);
        return deadlineAt != null && deadlineAt.isBefore(requested) ? deadlineAt : requested;
    }

    private boolean fenced(AgentRunLease lease) {
        return lease != null && lease.executionAttempt() > 0 && lease.leaseOwner() != null;
    }

    private AgentOrchestratorException leaseLost(AgentRunLease lease, LocalDateTime now) {
        if (lease != null && lease.deadlineReached(now)) {
            return new AgentOrchestratorException(
                    ErrorCode.UPSTREAM_ERROR, "AGENT_TIMEOUT", "Agent run deadline elapsed before response settlement");
        }
        return new AgentOrchestratorException(
                ErrorCode.CONFLICT, "AGENT_LEASE_LOST", "Agent run lease is no longer current");
    }

    public interface LeaseHeartbeat extends AutoCloseable {
        LeaseHeartbeat NOOP = () -> { };

        @Override
        void close();
    }

    @Transactional
    public AiAnalysisRun cancel(AuthenticatedUser user, Long runId) {
        AiAnalysisRun run = runMapper.selectOwnedAgentRunForUpdate(runId, user.userId());
        if (run == null) throw new BusinessException(ErrorCode.NOT_FOUND, "研究运行不存在");
        if (!java.util.Set.of("received", "running").contains(run.getStatus())) return run;
        if (runMapper.cancelOwnedAgentRun(runId, user.userId(), LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究运行状态已改变");
        }
        return runMapper.selectOwnedAgentRun(runId, user.userId());
    }

    private String visibleProgress(String stage) {
        return switch (stage) {
            case "received", "planning", "waiting_for_model" -> "正在分析需求";
            case "tool_requested", "tool_running" -> "正在检索并核验证据";
            case "synthesizing" -> "正在整理回答";
            default -> "正在处理研究任务";
        };
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
