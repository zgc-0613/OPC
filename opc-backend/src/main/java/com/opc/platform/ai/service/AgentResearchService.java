package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.ai.dto.AgentMessageCreateDTO;
import com.opc.platform.ai.dto.AgentSessionStartDTO;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AgentRuntimeConfigProvider;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

@Service
public class AgentResearchService {

    private final AgentSessionService sessionService;
    private final AiAgentMessageMapper messageMapper;
    private final AiAnalysisRunMapper runMapper;
    private final AgentRunLifecycleService lifecycle;
    private final AgentRuntimeConfigProvider configProvider;
    private final AgentRunDispatcher dispatcher;
    private final AgentClarificationPolicy clarificationPolicy;
    private final AgentProfilePolicy profilePolicy;
    private final AgentSessionHistoryService historyService;
    private final TransactionTemplate transactions;
    private final TaskExecutor executor;
    private final ObjectMapper objectMapper;
    private final PhaseThreeTaskContextValidator taskContextValidator;
    private final PhaseThreeSelectedEvidenceValidator selectedEvidenceValidator;

    @Autowired
    public AgentResearchService(
            AgentSessionService sessionService,
            AiAgentMessageMapper messageMapper,
            AiAnalysisRunMapper runMapper,
            AgentRunLifecycleService lifecycle,
            AgentRuntimeConfigProvider configProvider,
            AgentRunDispatcher dispatcher,
            AgentClarificationPolicy clarificationPolicy,
            AgentProfilePolicy profilePolicy,
            AgentSessionHistoryService historyService,
            TransactionTemplate transactions,
            @Qualifier("agentTaskExecutor") TaskExecutor executor,
            ObjectMapper objectMapper,
            PhaseThreeSelectedEvidenceValidator selectedEvidenceValidator
    ) {
        this.sessionService = sessionService;
        this.messageMapper = messageMapper;
        this.runMapper = runMapper;
        this.lifecycle = lifecycle;
        this.configProvider = configProvider;
        this.dispatcher = dispatcher;
        this.clarificationPolicy = clarificationPolicy;
        this.profilePolicy = profilePolicy;
        this.historyService = historyService;
        this.transactions = transactions;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.taskContextValidator = new PhaseThreeTaskContextValidator(objectMapper);
        this.selectedEvidenceValidator = selectedEvidenceValidator;
    }

    public AgentResearchReceipt submit(
            AuthenticatedUser user,
            Long sessionId,
            AgentMessageCreateDTO request
    ) {
        validateRequest(request);
        if (request.getTaskContext() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "taskContext 只能在新建研究时提交");
        }
        String requestedIntent = requestedIntent(request.getRequestedIntent());
        AgentSubmissionIdentity identity = new AgentSubmissionIdentity(
                "message", hash(request.getContent().trim()), null, 0L, requestedIntent);
        AgentRuntimeConfig config = configProvider.agentRuntimeConfig();
        if (!config.enabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Agent Runtime 尚未启用");
        }
        Submission submission;
        try {
            submission = transactions.execute(status -> createSubmission(user, sessionId, request, config, identity));
        } catch (ReusedSubmissionException exception) {
            return exception.receipt;
        }
        if (submission == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "研究运行未创建");
        if (submission.run() != null && !submission.reused()) {
            try {
                executor.execute(dispatcher::processNext);
            } catch (TaskRejectedException ignored) {
                // The scheduled database worker will claim the durable received run.
            }
        }
        return submission.receipt();
    }

    public AgentResearchStartReceipt start(AuthenticatedUser user, AgentSessionStartDTO request) {
        validateStartRequest(request);
        AgentAnalyticsSnapshotBinding analyticsSnapshot = request.getAnalyticsSnapshotBinding();
        validateAnalyticsSnapshotBinding(analyticsSnapshot);
        String requestedIntent = requestedIntent(request.getRequestedIntent());
        if (request.getTaskContext() != null && !request.getTaskContext().isNull()
                && "auto".equals(requestedIntent)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "PHASE3_TASK_INTENT_MISMATCH");
        }
        PhaseThreeTaskContext taskContext = normalizeTaskContext(request.getTaskContext(), requestedIntent);
        PhaseThreeTaskContext finalizedTaskContext = taskContext;
        String profileJson = profilePolicy.canonicalJson(request.getProfile());
        AgentSubmissionIdentity requestedIdentity = new AgentSubmissionIdentity(
                "session_start",
                hash(request.getContent().trim() + analyticsIdentitySuffix(analyticsSnapshot)),
                profilePolicy.fingerprint(profileJson),
                0L,
                requestedIntent
        ).withTaskContext(
                taskContext == null ? null : PhaseThreeTaskContextValidator.VERSION,
                taskContext == null ? null : taskContext.canonicalJson(),
                taskContext == null ? null : taskContext.hash())
                .withAnalyticsSnapshot(analyticsSnapshot);
        AgentRuntimeConfig config = configProvider.agentRuntimeConfig();
        if (!config.enabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Agent Runtime is not enabled");
        }
        AgentResearchReceipt receipt;
        try {
            receipt = transactions.execute(status -> createStartSubmission(
                    user, request, profileJson, requestedIdentity, config, finalizedTaskContext));
        } catch (ReusedSubmissionException exception) {
            receipt = exception.receipt;
        }
        if (receipt == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Research run was not created");
        }
        AiAgentSession session = sessionService.requireOwned(user, receipt.sessionId());
        if (session.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "The idempotency key belongs to a deleted session");
        }
        if (java.util.Set.of("received", "running").contains(receipt.status())) {
            session.setActiveRunStatus(receipt.status());
        }
        if (java.util.Set.of("received", "running").contains(receipt.status())) {
            try {
                executor.execute(dispatcher::processNext);
            } catch (TaskRejectedException ignored) {
                // The scheduled database worker will claim the durable received run.
            }
        }
        var sessionView = historyService.view(session);
        JsonNode storedTaskContext = parseTaskContext(session.getTaskContextJson());
        String taskType = storedTaskContext != null && storedTaskContext.path("taskType").isTextual()
                ? storedTaskContext.path("taskType").asText() : requestedIntent;
        return new AgentResearchStartReceipt(
                sessionView, receipt.messageId(), receipt.runId(), receipt.status(),
                taskType, session.getTaskContextHash(), storedTaskContext);
    }

    private AgentResearchReceipt createStartSubmission(
            AuthenticatedUser user,
            AgentSessionStartDTO request,
            String profileJson,
            AgentSubmissionIdentity requestedIdentity,
            AgentRuntimeConfig config,
            PhaseThreeTaskContext taskContext
    ) {
        sessionService.lockHistoryRevision(user);
        AiAnalysisRun existing = runMapper.findAgentByIdempotency(user.userId(), request.getIdempotencyKey());
        if (existing != null) {
            validateReplay(user, existing, null, requestedIdentity);
            return receipt(existing);
        }
        selectedEvidenceValidator.validate(taskContext);
        AiAgentSession session = sessionService.create(
                user, null, profileJson, requestedIdentity.taskContextVersion(),
                requestedIdentity.taskContextJson(), requestedIdentity.taskContextHash());
        AgentMessageCreateDTO message = new AgentMessageCreateDTO();
        message.setContent(request.getContent());
        message.setIdempotencyKey(request.getIdempotencyKey());
        message.setRequestedIntent(requestedIdentity.requestedIntent());
        AgentSubmissionIdentity identity = new AgentSubmissionIdentity(
                requestedIdentity.kind(), requestedIdentity.contentHash(), requestedIdentity.profileHash(),
                session.getContentGeneration() == null ? 0L : session.getContentGeneration(),
                requestedIdentity.requestedIntent(), requestedIdentity.taskContextVersion(),
                requestedIdentity.taskContextJson(), requestedIdentity.taskContextHash())
                .withAnalyticsSnapshot(requestedIdentity.analyticsSnapshot());
        Submission submission = createSubmission(user, session.getId(), message, config, identity);
        if (submission.reused()) {
            throw new ReusedSubmissionException(submission.receipt());
        }
        return submission.receipt();
    }

    private Submission createSubmission(
            AuthenticatedUser user,
            Long sessionId,
            AgentMessageCreateDTO request,
            AgentRuntimeConfig config,
            AgentSubmissionIdentity identity
    ) {
        AiAnalysisRun existing = runMapper.findAgentByIdempotency(user.userId(), request.getIdempotencyKey());
        if (existing != null) {
            validateReplay(user, existing, "message".equals(identity.kind()) ? sessionId : null, identity);
            return new Submission(receipt(existing), null, null, true);
        }
        AiAgentSession session = sessionService.lockOwned(user, sessionId);
        if (!"active".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已归档会话不能继续发送消息");
        }
        if (clarificationPolicy.changesResearchBoundary(session.getProfileJson(), request.getContent())) {
            throw new BusinessException(ErrorCode.CONFLICT, "需要基于新条件创建研究");
        }
        AiAgentMessage userMessage = sessionService.appendMessage(
                user, sessionId, "user", request.getContent(), "completed", null, null);
        AgentClarificationDecision clarification = clarificationPolicy.evaluate(
                session.getProfileJson(), session.getResearchContextJson(), request.getContent());
        sessionService.updateResearchContext(user, sessionId, clarification.contextJson());
        if (clarification.evidenceInsufficient()) {
            String answer = "补充信息仍无法唯一匹配到已核验的地区或行业，本次研究无法安全继续。";
            AiAnalysisRun run = clarificationRun(user, sessionId, userMessage.getId(),
                    request.getIdempotencyKey(), "evidence_insufficient", identity);
            messageMapper.attachRun(userMessage.getId(), run.getId());
            AiAgentMessage assistant = sessionService.appendMessage(
                    user, sessionId, "assistant", answer, "completed", run.getId(), "[]");
            run.setResultJson(safeResultJson(assistant.getId(), 0));
            runMapper.updateById(run);
            return new Submission(new AgentResearchReceipt(
                    sessionId, userMessage.getId(), run.getId(), "evidence_insufficient"), null,
                    clarification.contextJson(), false);
        }
        if (clarification.question() != null) {
            AiAnalysisRun run = clarificationRun(
                    user, sessionId, userMessage.getId(), request.getIdempotencyKey(), identity);
            messageMapper.attachRun(userMessage.getId(), run.getId());
            AiAgentMessage assistant = sessionService.appendMessage(
                    user, sessionId, "assistant", clarification.question(), "completed", run.getId(), "[]");
            run.setResultJson(safeResultJson(assistant.getId(), 0));
            runMapper.updateById(run);
            return new Submission(new AgentResearchReceipt(
                    sessionId, userMessage.getId(), run.getId(), "clarification_needed"), null,
                    clarification.contextJson(), false);
        }
        AiAnalysisRun run = lifecycle.enqueue(
                user, sessionId, userMessage.getId(), request.getIdempotencyKey(), config, identity);
        if (!Objects.equals(run.getUserMessageId(), userMessage.getId())) {
            validateReplay(user, run, "message".equals(identity.kind()) ? sessionId : null, identity);
            throw new ReusedSubmissionException(receipt(run));
        }
        if (messageMapper.attachRun(userMessage.getId(), run.getId()) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户消息运行关联失败");
        }
        return new Submission(new AgentResearchReceipt(
                sessionId, userMessage.getId(), run.getId(), "received"), run,
                session.getProfileJson(), false);
    }

    private AiAnalysisRun clarificationRun(
            AuthenticatedUser user,
            Long sessionId,
            Long userMessageId,
            String idempotencyKey,
            AgentSubmissionIdentity identity
    ) {
        return clarificationRun(
                user, sessionId, userMessageId, idempotencyKey, "clarification_needed", identity);
    }

    private AiAnalysisRun clarificationRun(
            AuthenticatedUser user,
            Long sessionId,
            Long userMessageId,
            String idempotencyKey,
            String terminalStatus,
            AgentSubmissionIdentity identity
    ) {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setUserId(user.userId());
        run.setTaskType("agent_research");
        run.setSessionId(sessionId);
        run.setUserMessageId(userMessageId);
        run.setIdempotencyKey(idempotencyKey);
        run.setSubmissionKind(identity.kind());
        run.setRequestedIntent(identity.requestedIntent());
        run.setRequestContentHash(identity.contentHash());
        run.setStartProfileHash(identity.profileHash());
        run.setSessionContentGeneration(identity.sessionContentGeneration());
        AgentAnalyticsSnapshotBinding analyticsSnapshot = identity.analyticsSnapshot();
        run.setAnalyticsSnapshotId(analyticsSnapshot == null ? null : analyticsSnapshot.snapshotId());
        run.setAnalyticsMetricId(analyticsSnapshot == null ? null : analyticsSnapshot.metricId());
        run.setAnalyticsDataVersion(analyticsSnapshot == null ? null : analyticsSnapshot.dataVersion());
        run.setAnalyticsFiltersJson(analyticsSnapshot == null ? null : analyticsSnapshot.filtersJson());
        run.setAnalyticsSnapshotJson(analyticsSnapshot == null ? null : analyticsSnapshot.snapshotJson());
        run.setStatus(terminalStatus);
        run.setProvider("not_called");
        run.setModelId("not_called");
        run.setPromptVersion("agent-research-v2");
        run.setEvidenceHash(hash(sessionId + ":" + userMessageId + ":clarification"));
        run.setCurrentStage(terminalStatus);
        run.setVisibleProgress("需要补充一项信息");
        run.setPromptTokens(0);
        run.setCompletionTokens(0);
        run.setTotalTokens(0);
        run.setReservedTokens(0L);
        run.setStepCount(0);
        run.setToolCallCount(0);
        run.setCompletedAt(LocalDateTime.now());
        try {
            runMapper.insert(run);
        } catch (DuplicateKeyException exception) {
            AiAnalysisRun existing = runMapper.findAgentByIdempotency(user.userId(), idempotencyKey);
            if (existing == null) throw exception;
            validateReplay(user, existing, "message".equals(identity.kind()) ? sessionId : null, identity);
            throw new ReusedSubmissionException(receipt(existing));
        }
        return run;
    }

    private void validateStartRequest(AgentSessionStartDTO request) {
        if (request == null || !StringUtils.hasText(request.getContent())
                || request.getContent().trim().length() > AgentSessionService.MAX_USER_MESSAGE_LENGTH
                || !StringUtils.hasText(request.getIdempotencyKey())
                || !request.getIdempotencyKey().matches("[A-Za-z0-9_-]{8,64}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Research message or idempotency key is invalid");
        }
    }

    private void validateReplay(
            AuthenticatedUser user,
            AiAnalysisRun existing,
            Long expectedSessionId,
            AgentSubmissionIdentity expected
    ) {
        AiAgentSession storedSession = "session_start".equals(expected.kind())
                ? sessionService.requireOwned(user, existing.getSessionId()) : null;
        String storedKind = StringUtils.hasText(existing.getSubmissionKind())
                ? existing.getSubmissionKind() : "message";
        String storedContentHash = existing.getRequestContentHash();
        if (!StringUtils.hasText(storedContentHash) && existing.getUserMessageId() != null) {
            AiAgentMessage storedMessage = messageMapper.selectById(existing.getUserMessageId());
            if (storedMessage != null && StringUtils.hasText(storedMessage.getContent())) {
                storedContentHash = hash(storedMessage.getContent().trim());
            }
        }
        boolean mismatch = !storedKind.equals(expected.kind())
                || !ResearchExecutionRequirements.normalizeIntent(existing.getRequestedIntent())
                    .equals(ResearchExecutionRequirements.normalizeIntent(expected.requestedIntent()))
                || !Objects.equals(storedContentHash, expected.contentHash())
                || value(existing.getSessionContentGeneration()) != expected.sessionContentGeneration()
                || (expectedSessionId != null && !Objects.equals(existing.getSessionId(), expectedSessionId))
                || ("session_start".equals(expected.kind())
                    && (!Objects.equals(existing.getStartProfileHash(), expected.profileHash())
                        || !Objects.equals(storedSession.getTaskContextVersion(), expected.taskContextVersion())
                        || !taskContextMatches(storedSession, expected)
                        || !analyticsBindingMatches(existing, expected.analyticsSnapshot())));
        if (mismatch) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "The idempotency key is already bound to a different research request");
        }
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private boolean taskContextMatches(AiAgentSession session, AgentSubmissionIdentity expected) {
        if (StringUtils.hasText(session.getTaskContextHash()) || StringUtils.hasText(expected.taskContextHash())) {
            return Objects.equals(session.getTaskContextHash(), expected.taskContextHash());
        }
        return Objects.equals(session.getTaskContextJson(), expected.taskContextJson());
    }

    private boolean analyticsBindingMatches(AiAnalysisRun run, AgentAnalyticsSnapshotBinding binding) {
        if (binding == null) {
            return run.getAnalyticsSnapshotId() == null
                    && run.getAnalyticsMetricId() == null
                    && run.getAnalyticsDataVersion() == null
                    && run.getAnalyticsFiltersJson() == null;
        }
        return Objects.equals(run.getAnalyticsSnapshotId(), binding.snapshotId())
                && Objects.equals(run.getAnalyticsMetricId(), binding.metricId())
                && Objects.equals(run.getAnalyticsDataVersion(), binding.dataVersion())
                && Objects.equals(run.getAnalyticsFiltersJson(), binding.filtersJson())
                && Objects.equals(run.getAnalyticsSnapshotJson(), binding.snapshotJson());
    }

    private void validateAnalyticsSnapshotBinding(AgentAnalyticsSnapshotBinding binding) {
        if (binding == null) return;
        if (binding.snapshotId() == null || binding.snapshotId() <= 0
                || !StringUtils.hasText(binding.metricId()) || !binding.metricId().matches("[A-Za-z0-9._-]{1,80}")
                || !StringUtils.hasText(binding.dataVersion()) || !binding.dataVersion().matches("[A-Za-z0-9:._-]{1,128}")
                || !StringUtils.hasText(binding.filtersJson()) || binding.filtersJson().length() > 8000
                || !StringUtils.hasText(binding.snapshotJson()) || binding.snapshotJson().length() > 16000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_SNAPSHOT_INVALID");
        }
        try {
            JsonNode filters = objectMapper.readTree(binding.filtersJson());
            JsonNode snapshot = objectMapper.readTree(binding.snapshotJson());
            if (!filters.isObject() || !snapshot.isObject()
                    || !binding.metricId().equals(snapshot.path("metricId").asText())
                    || !binding.dataVersion().equals(snapshot.path("dataVersion").asText())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_SNAPSHOT_INVALID");
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_SNAPSHOT_INVALID");
        }
    }

    private String analyticsIdentitySuffix(AgentAnalyticsSnapshotBinding binding) {
        if (binding == null) return "";
        return "\nanalyticsSnapshot=" + binding.snapshotId()
                + "\nmetricId=" + binding.metricId()
                + "\ndataVersion=" + binding.dataVersion()
                + "\nfilters=" + binding.filtersJson()
                + "\nsnapshot=" + binding.snapshotJson();
    }

    private void validateRequest(AgentMessageCreateDTO request) {
        if (request == null || !StringUtils.hasText(request.getContent())
                || request.getContent().trim().length() > AgentSessionService.MAX_USER_MESSAGE_LENGTH
                || !StringUtils.hasText(request.getIdempotencyKey())
                || !request.getIdempotencyKey().matches("[A-Za-z0-9_-]{8,64}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "研究消息或幂等键格式无效");
        }
    }

    private PhaseThreeTaskContext normalizeTaskContext(JsonNode raw, String requestedIntent) {
        if (raw == null || raw.isNull()) return null;
        return taskContextValidator.validateAndNormalize(raw, requestedIntent);
    }

    private String requestedIntent(String value) {
        String normalized = ResearchExecutionRequirements.normalizeIntent(value);
        if (!AgentResearchContract.REQUESTED_INTENTS.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "requestedIntent is invalid");
        }
        return normalized;
    }

    private AgentResearchReceipt receipt(AiAnalysisRun run) {
        return new AgentResearchReceipt(run.getSessionId(), run.getUserMessageId(), run.getId(), run.getStatus());
    }

    private String safeResultJson(Long messageId, int citationCount) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "finalMessageId", messageId, "citationCount", citationCount));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "研究审计摘要无法序列化");
        }
    }

    private JsonNode parseTaskContext(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            JsonNode parsed = objectMapper.readTree(value);
            return parsed.isObject() ? parsed : null;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Stored research context is invalid");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record Submission(
            AgentResearchReceipt receipt,
            AiAnalysisRun run,
            String profileJson,
            boolean reused
    ) {
    }

    private static final class ReusedSubmissionException extends RuntimeException {
        private final AgentResearchReceipt receipt;
        private ReusedSubmissionException(AgentResearchReceipt receipt) { this.receipt = receipt; }
    }
}
