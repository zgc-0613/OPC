package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.AgentMessageCreateDTO;
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
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
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
    private final TransactionTemplate transactions;
    private final TaskExecutor executor;
    private final ObjectMapper objectMapper;

    public AgentResearchService(
            AgentSessionService sessionService,
            AiAgentMessageMapper messageMapper,
            AiAnalysisRunMapper runMapper,
            AgentRunLifecycleService lifecycle,
            AgentRuntimeConfigProvider configProvider,
            AgentRunDispatcher dispatcher,
            AgentClarificationPolicy clarificationPolicy,
            TransactionTemplate transactions,
            @Qualifier("agentTaskExecutor") TaskExecutor executor,
            ObjectMapper objectMapper
    ) {
        this.sessionService = sessionService;
        this.messageMapper = messageMapper;
        this.runMapper = runMapper;
        this.lifecycle = lifecycle;
        this.configProvider = configProvider;
        this.dispatcher = dispatcher;
        this.clarificationPolicy = clarificationPolicy;
        this.transactions = transactions;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    public AgentResearchReceipt submit(
            AuthenticatedUser user,
            Long sessionId,
            AgentMessageCreateDTO request
    ) {
        validateRequest(request);
        AgentRuntimeConfig config = configProvider.agentRuntimeConfig();
        if (!config.enabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Agent Runtime 尚未启用");
        }
        Submission submission;
        try {
            submission = transactions.execute(status -> createSubmission(user, sessionId, request, config));
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

    private Submission createSubmission(
            AuthenticatedUser user,
            Long sessionId,
            AgentMessageCreateDTO request,
            AgentRuntimeConfig config
    ) {
        AiAnalysisRun existing = runMapper.findAgentByIdempotency(user.userId(), request.getIdempotencyKey());
        if (existing != null) {
            return new Submission(receipt(existing), null, null, true);
        }
        AiAgentSession session = sessionService.lockOwned(user, sessionId);
        if (!"active".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已归档会话不能继续发送消息");
        }
        AiAgentMessage userMessage = sessionService.appendMessage(
                user, sessionId, "user", request.getContent(), "completed", null, null);
        AgentClarificationDecision clarification = clarificationPolicy.evaluate(
                session.getProfileJson(), session.getResearchContextJson(), request.getContent());
        sessionService.updateResearchContext(user, sessionId, clarification.contextJson());
        if (clarification.evidenceInsufficient()) {
            String answer = "补充信息仍无法唯一匹配到已核验的地区或行业，本次研究无法安全继续。";
            AiAnalysisRun run = clarificationRun(user, sessionId, userMessage.getId(),
                    request.getIdempotencyKey(), "evidence_insufficient");
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
            AiAnalysisRun run = clarificationRun(user, sessionId, userMessage.getId(), request.getIdempotencyKey());
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
                user, sessionId, userMessage.getId(), request.getIdempotencyKey(), config);
        if (!Objects.equals(run.getUserMessageId(), userMessage.getId())) {
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
            String idempotencyKey
    ) {
        return clarificationRun(user, sessionId, userMessageId, idempotencyKey, "clarification_needed");
    }

    private AiAnalysisRun clarificationRun(
            AuthenticatedUser user,
            Long sessionId,
            Long userMessageId,
            String idempotencyKey,
            String terminalStatus
    ) {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setUserId(user.userId());
        run.setTaskType("agent_research");
        run.setSessionId(sessionId);
        run.setUserMessageId(userMessageId);
        run.setIdempotencyKey(idempotencyKey);
        run.setStatus(terminalStatus);
        run.setProvider("not_called");
        run.setModelId("not_called");
        run.setPromptVersion("agent-research-v1");
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
        runMapper.insert(run);
        return run;
    }

    private void validateRequest(AgentMessageCreateDTO request) {
        if (request == null || !StringUtils.hasText(request.getContent())
                || request.getContent().trim().length() > AgentSessionService.MAX_USER_MESSAGE_LENGTH
                || !StringUtils.hasText(request.getIdempotencyKey())
                || !request.getIdempotencyKey().matches("[A-Za-z0-9_-]{8,64}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "研究消息或幂等键格式无效");
        }
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
