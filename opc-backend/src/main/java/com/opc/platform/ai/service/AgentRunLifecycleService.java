package com.opc.platform.ai.service;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AgentRunLifecycleService {

    private static final String PROMPT_VERSION = "agent-research-v1";

    private final AiAnalysisRunMapper runMapper;
    private final AiClient aiClient;
    private final AiRuntimeSettingsProvider settingsProvider;
    private final AiAgentProviderCallMapper providerCallMapper;

    @Autowired
    public AgentRunLifecycleService(
            AiAnalysisRunMapper runMapper,
            AiClient aiClient,
            AiRuntimeSettingsProvider settingsProvider,
            AiAgentProviderCallMapper providerCallMapper
    ) {
        this.runMapper = runMapper;
        this.aiClient = aiClient;
        this.settingsProvider = settingsProvider;
        this.providerCallMapper = providerCallMapper;
    }

    public AgentRunLifecycleService(
            AiAnalysisRunMapper runMapper,
            AiClient aiClient,
            AiRuntimeSettingsProvider settingsProvider
    ) {
        this(runMapper, aiClient, settingsProvider, null);
    }

    public AgentRunLease begin(
            AuthenticatedUser user,
            Long sessionId,
            Long userMessageId,
            String idempotencyKey,
            AgentRuntimeConfig config
    ) {
        runMapper.failExpiredRunning(LocalDateTime.now());
        return reserve(user, sessionId, userMessageId, idempotencyKey, config, "running");
    }

    public AiAnalysisRun enqueue(
            AuthenticatedUser user,
            Long sessionId,
            Long userMessageId,
            String idempotencyKey,
            AgentRuntimeConfig config
    ) {
        return reserve(user, sessionId, userMessageId, idempotencyKey, config, "received").run();
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
        if (runMapper.renewAgentLease(
                run.getId(), run.getLeaseOwner(), now, now.plus(config.timeout())) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent run lease expired before execution");
        }
        run.setHeartbeatAt(now);
        run.setLeaseExpiresAt(now.plus(config.timeout()));
        return new AgentRunLease(run, runtime, descriptor, config);
    }

    private AgentRunLease reserve(
            AuthenticatedUser user,
            Long sessionId,
            Long userMessageId,
            String idempotencyKey,
            AgentRuntimeConfig config,
            String initialStatus
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
        run.setStatus(initialStatus);
        run.setProvider(descriptor.provider());
        run.setModelId(descriptor.model());
        run.setPromptVersion(PROMPT_VERSION);
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
        if (providerCallMapper != null) {
            return invokeAudited(lease, request);
        }
        AiProviderResponse response = aiClient.generate(request, lease.runtime());
        int total = Math.max(Math.max(0, response.totalTokens()),
                Math.max(0, response.promptTokens()) + Math.max(0, response.completionTokens()));
        LocalDateTime now = LocalDateTime.now();
        int updated = runMapper.recordAgentUsage(
                lease.run().getId(), Math.max(0, response.promptTokens()), Math.max(0, response.completionTokens()),
                total, Math.max(0, response.latencyMs()), response.requestId(), response.finishReason(), now
        );
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究运行已取消、失败或过期，迟到结果已丢弃");
        }
        lease.add(response);
        return response;
    }

    private AiProviderResponse invokeAudited(AgentRunLease lease, AiProviderRequest request) {
        LocalDateTime dispatchedAt = LocalDateTime.now();
        if (runMapper.markAgentProviderDispatched(lease.run().getId(), dispatchedAt) != 1) {
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
        providerCallMapper.insert(call);

        AiProviderResponse response = aiClient.generate(request, lease.runtime());
        int prompt = Math.max(0, response.promptTokens());
        int completion = Math.max(0, response.completionTokens());
        int total = Math.max(Math.max(0, response.totalTokens()), prompt + completion);
        String providerRequestId = response.requestId() == null || response.requestId().isBlank()
                ? "not_provided" : response.requestId();
        LocalDateTime settledAt = LocalDateTime.now();
        if (providerCallMapper.settleActual(
                call.getId(), prompt, completion, total, Math.max(0, response.latencyMs()),
                providerRequestId, response.finishReason(), settledAt) == 1) {
            if (runMapper.settleAgentUsageActual(
                    lease.run().getId(), prompt, completion, total, Math.max(0, response.latencyMs()),
                    providerRequestId, response.finishReason(), settledAt) != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "Agent usage settlement state changed");
            }
            lease.add(response);
        }
        return response;
    }

    @Transactional
    public boolean settleLateUsage(Long providerCallId, AiProviderResponse response) {
        if (providerCallMapper == null || providerCallId == null || response == null) return false;
        AiAgentProviderCall call = providerCallMapper.selectForUpdate(providerCallId);
        if (call == null || !"settled_estimated".equals(call.getSettlementStatus())) return false;
        int prompt = Math.max(0, response.promptTokens());
        int completion = Math.max(0, response.completionTokens());
        int total = Math.max(Math.max(0, response.totalTokens()), prompt + completion);
        String providerRequestId = response.requestId() == null || response.requestId().isBlank()
                ? "not_provided" : response.requestId();
        LocalDateTime now = LocalDateTime.now();
        if (providerCallMapper.replaceEstimateWithActual(
                providerCallId, prompt, completion, total, Math.max(0, response.latencyMs()),
                providerRequestId, response.finishReason(), now) != 1) return false;
        if (runMapper.reconcileAgentProviderUsage(call.getAnalysisRunId(), now) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent usage reconciliation failed");
        }
        return true;
    }

    public void updateStage(AgentRunLease lease, String stage, int stepCount, int toolCallCount) {
        if (runMapper.updateAgentStage(
                lease.run().getId(), stage, visibleProgress(stage), stepCount, toolCallCount, LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究运行已取消、失败或过期");
        }
        lease.updateProgress(stepCount, toolCallCount);
    }

    public void complete(AgentRunLease lease, AgentOrchestratorOutcome outcome, String resultJson) {
        if (runMapper.settleAgentCompleted(
                lease.run().getId(), outcome.status(), outcome.promptTokens(), outcome.completionTokens(),
                outcome.totalTokens(), outcome.latencyMs(), outcome.requestId(), outcome.finishReason(),
                outcome.modelRounds(), outcome.toolCallCount(), resultJson, LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究运行已取消、失败或过期，完成结果未写入");
        }
    }

    public void fail(AgentRunLease lease, String status, ErrorCode errorCode, String diagnosticCode) {
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
