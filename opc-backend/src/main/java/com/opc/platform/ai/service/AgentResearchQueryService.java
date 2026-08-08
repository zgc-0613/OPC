package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.AgentSessionCreateDTO;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.vo.AgentMessageVO;
import com.opc.platform.ai.vo.AgentRunStatusVO;
import com.opc.platform.ai.vo.AgentSessionDetailVO;
import com.opc.platform.ai.vo.AgentSessionVO;
import com.opc.platform.ai.vo.AgentToolCallSummaryVO;
import com.opc.platform.ai.vo.AiTokenUsageVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentResearchQueryService {

    private static final Set<String> PROFILE_FIELDS = Set.of(
            "ventureType", "regionId", "industryTagId", "industry", "stage",
            "budgetRange", "goal", "resources"
    );

    private final AgentSessionService sessionService;
    private final AgentRunLifecycleService lifecycle;
    private final AiAgentMessageMapper messageMapper;
    private final AiAgentToolCallMapper toolCallMapper;
    private final AiAnalysisRunMapper runMapper;
    private final ObjectMapper objectMapper;
    private final AgentProfilePolicy profilePolicy;
    private final AgentRunFeedbackService feedbackService;

    public AgentSessionVO createSession(AuthenticatedUser user, AgentSessionCreateDTO request) {
        String profileJson = profilePolicy.canonicalJson(request == null ? null : request.getProfile());
        return toSession(sessionService.create(user, request == null ? null : request.getTitle(), profileJson), true);
    }

    public List<AgentSessionVO> listSessions(AuthenticatedUser user) {
        return sessionService.list(user).stream().map(session -> toSession(session, false)).toList();
    }

    public AgentSessionDetailVO sessionDetail(AuthenticatedUser user, Long sessionId) {
        AiAgentSession session = sessionService.requireOwned(user, sessionId);
        List<AiAgentMessage> descending = messageMapper.selectMessagePage(sessionId, null, 51);
        List<AiAgentMessage> safe = descending == null ? List.of() : descending;
        boolean hasMore = safe.size() > 50;
        List<AiAgentMessage> messages = new java.util.ArrayList<>(safe.subList(0, Math.min(50, safe.size())));
        java.util.Collections.reverse(messages);
        AiAnalysisRun activeRun = runMapper.selectActiveAgentRunForSession(sessionId);
        AiAnalysisRun latest = runMapper.selectLatestAgentRunForSession(sessionId);
        AgentRunStatusVO active = activeRun == null ? null : toRun(activeRun, session);
        session.setActiveRunStatus(activeRun == null ? null : activeRun.getStatus());
        return new AgentSessionDetailVO(
                toSession(session, true),
                (messages == null ? List.<AiAgentMessage>of() : messages).stream().map(this::toMessage).toList(),
                hasMore && !messages.isEmpty() ? messages.get(0).getSequenceNo() : null,
                hasMore,
                active,
                latest == null ? null : toRun(latest, session)
        );
    }

    public void archiveSession(AuthenticatedUser user, Long sessionId) {
        sessionService.archive(user, sessionId);
    }

    public AgentRunStatusVO run(AuthenticatedUser user, Long runId) {
        AiAnalysisRun run = runMapper.selectOwnedAgentRun(runId, user.userId());
        if (run == null) throw new BusinessException(ErrorCode.NOT_FOUND, "研究运行不存在");
        return toRun(run, sessionService.requireOwned(user, run.getSessionId()));
    }

    @Transactional
    public AgentRunStatusVO cancel(AuthenticatedUser user, Long runId) {
        AiAnalysisRun existing = runMapper.selectOwnedAgentRun(runId, user.userId());
        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND, "研究运行不存在");
        sessionService.lockOwned(user, existing.getSessionId());
        lifecycle.cancel(user, runId);
        return run(user, runId);
    }

    private AgentRunStatusVO toRun(AiAnalysisRun run, AiAgentSession session) {
        AiAgentMessage userMessage = run.getUserMessageId() == null
                ? null : messageMapper.selectById(run.getUserMessageId());
        AiAgentMessage finalMessage = messageMapper.selectFinalByRun(run.getId());
        List<AiAgentToolCall> calls = toolCallMapper.selectByRunId(run.getId());
        JsonNode result = parseJson(run.getResultJson(), true);
        JsonNode structuredResult = result.path("structuredResult").isObject()
                ? result.path("structuredResult") : null;
        JsonNode taskContext = parseOptionalJson(session.getTaskContextJson());
        String taskType = taskType(run, taskContext, structuredResult);
        return new AgentRunStatusVO(
                run.getId(), run.getSessionId(), run.getStatus(),
                retryContent(run, userMessage),
                run.getCurrentStage() == null ? run.getStatus() : run.getCurrentStage(),
                safe(run.getStepCount()), safe(run.getToolCallCount()), run.getVisibleProgress(),
                researchPlan(taskType),
                finalMessage == null ? null : toMessage(finalMessage),
                finalMessage == null ? objectMapper.createArrayNode()
                        : parseJson(finalMessage.getCitationsJson(), false),
                (calls == null ? List.<AiAgentToolCall>of() : calls).stream().map(this::toTool).toList(),
                new AiTokenUsageVO(safe(run.getPromptTokens()), safe(run.getCompletionTokens()), safe(run.getTotalTokens())),
                run.getProvider(), run.getModelId(), run.getPromptVersion(), run.getDiagnosticCode(),
                run.getFinishReason(), run.getProviderRequestId(), run.getLatencyMs(),
                structuredResult,
                analyticsSnapshot(result),
                run.getCreatedAt(), run.getCompletedAt(),
                taskType, session.getTaskContextHash(),
                feedbackService.feedbackEligible(run, finalMessage),
                run.getDeadlineAt()
        );
    }

    private String taskType(AiAnalysisRun run, JsonNode taskContext, JsonNode structuredResult) {
        String taskType = taskContext != null && taskContext.path("taskType").isTextual()
                ? taskContext.path("taskType").asText() : run.getRequestedIntent();
        if (taskType == null || taskType.isBlank() || "auto".equals(taskType)) {
            taskType = structuredResult != null && structuredResult.path("intent").isTextual()
                    ? structuredResult.path("intent").asText() : "general_research";
        }
        return taskType;
    }

    private List<String> researchPlan(String taskType) {
        return switch (taskType) {
            case "case_analysis" -> List.of(
                    "理解案例分析目标与适用条件", "检索并核对目标案例", "核验关键事实与来源", "整理可借鉴做法、风险与行动");
            case "case_comparison" -> List.of(
                    "理解比较对象与统一维度", "检索并核对各案例", "比较差异、共性与证据强弱", "整理适用场景、风险与行动");
            case "technology_assessment" -> List.of(
                    "理解技术目标与约束", "检索相关案例、政策与来源", "评估成熟度、成本、依赖与替代路线", "整理风险与实施建议");
            case "policy_lookup" -> List.of(
                    "理解研究目标与适用条件", "检索匹配政策", "核验政策来源与时效", "整理适配性、限制与申请路径");
            case "source_verification" -> List.of(
                    "理解需要核验的结论或来源", "读取来源身份与发布时间", "检查链接、证据链与交叉证据", "整理可信度、冲突与限制");
            default -> List.of(
                    "理解研究目标与边界", "检索匹配案例与政策", "核验关键来源与证据覆盖", "整理事实、推断、建议与风险");
        };
    }

    private String retryContent(AiAnalysisRun run, AiAgentMessage userMessage) {
        if (userMessage == null || !Set.of(
                "failed", "expired", "cancelled", "evidence_insufficient").contains(run.getStatus())) {
            return null;
        }
        return userMessage.getContent();
    }

    private AgentToolCallSummaryVO toTool(AiAgentToolCall call) {
        return new AgentToolCallSummaryVO(
                call.getId(), call.getStepNo(), call.getToolName(), call.getStatus(),
                safe(call.getEvidenceCount()), call.getLatencyMs(), call.getEvidenceHash(), call.getDiagnosticCode()
        );
    }

    private AgentSessionVO toSession(AiAgentSession session, boolean includeTaskContext) {
        JsonNode taskContext = parseOptionalJson(session.getTaskContextJson());
        String taskType = taskContext != null && taskContext.path("taskType").isTextual()
                ? taskContext.path("taskType").asText() : null;
        return new AgentSessionVO(
                session.getId(), session.getTitle(), session.getTitleMode(), session.getStatus(),
                parseJson(session.getProfileJson(), true), session.getPinnedAt() != null,
                session.getArchivedAt(), session.getDeletedAt(), session.getPurgeAfter(),
                session.getActiveRunStatus(),
                session.getCreatedAt(), session.getUpdatedAt(), session.getLastMessageAt(),
                includeTaskContext ? taskContext : null,
                includeTaskContext ? session.getTaskContextVersion() : null,
                includeTaskContext ? session.getTaskContextHash() : null,
                taskType
        );
    }

    private AgentMessageVO toMessage(AiAgentMessage message) {
        return new AgentMessageVO(
                message.getId(), message.getRole(), message.getContent(), message.getStatus(),
                message.getSequenceNo(), message.getRunId(), parseJson(message.getCitationsJson(), false),
                messageStructuredResult(message),
                messageAnalyticsSnapshot(message),
                message.getCreatedAt()
        );
    }

    private JsonNode messageStructuredResult(AiAgentMessage message) {
        if (!"assistant".equals(message.getRole()) || !"completed".equals(message.getStatus())) return null;
        JsonNode result = parseOptionalJson(message.getStructuredResultJson());
        return result != null && result.isObject() ? result : null;
    }

    private JsonNode messageAnalyticsSnapshot(AiAgentMessage message) {
        if (!"assistant".equals(message.getRole()) || !"completed".equals(message.getStatus())) return null;
        JsonNode snapshot = parseOptionalJson(message.getAnalyticsSnapshotJson());
        return snapshot != null && snapshot.isObject() ? snapshot : null;
    }

    private JsonNode analyticsSnapshot(JsonNode result) {
        JsonNode snapshot = result == null ? null : result.path("analyticsSnapshot");
        return snapshot != null && snapshot.isObject() ? snapshot : null;
    }

    private String validateAndWriteProfile(JsonNode profile) {
        if (profile == null || profile.isNull()) return null;
        if (!profile.isObject()) throw new BusinessException(ErrorCode.BAD_REQUEST, "研究画像必须是对象");
        var fields = profile.fieldNames();
        while (fields.hasNext()) {
            if (!PROFILE_FIELDS.contains(fields.next())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "研究画像包含未知字段");
            }
        }
        String value = profile.toString();
        if (value.length() > 8000) throw new BusinessException(ErrorCode.BAD_REQUEST, "研究画像内容过长");
        return value;
    }

    private JsonNode parseJson(String value, boolean objectFallback) {
        try {
            return value == null ? (objectFallback ? objectMapper.createObjectNode() : objectMapper.createArrayNode())
                    : objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            return objectFallback ? objectMapper.createObjectNode() : objectMapper.createArrayNode();
        }
    }

    private JsonNode parseOptionalJson(String value) {
        return value == null ? null : parseJson(value, true);
    }

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
