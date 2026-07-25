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

    public AgentSessionVO createSession(AuthenticatedUser user, AgentSessionCreateDTO request) {
        String profileJson = validateAndWriteProfile(request == null ? null : request.getProfile());
        return toSession(sessionService.create(user, request == null ? null : request.getTitle(), profileJson));
    }

    public List<AgentSessionVO> listSessions(AuthenticatedUser user) {
        return sessionService.list(user).stream().map(this::toSession).toList();
    }

    public AgentSessionDetailVO sessionDetail(AuthenticatedUser user, Long sessionId) {
        AiAgentSession session = sessionService.requireOwned(user, sessionId);
        List<AiAgentMessage> descending = messageMapper.selectMessagePage(sessionId, null, 51);
        List<AiAgentMessage> safe = descending == null ? List.of() : descending;
        boolean hasMore = safe.size() > 50;
        List<AiAgentMessage> messages = new java.util.ArrayList<>(safe.subList(0, Math.min(50, safe.size())));
        java.util.Collections.reverse(messages);
        AiAnalysisRun latest = runMapper.selectLatestAgentRunForSession(sessionId);
        AgentRunStatusVO active = latest != null && Set.of("received", "running").contains(latest.getStatus())
                ? toRun(latest) : null;
        return new AgentSessionDetailVO(
                toSession(session),
                (messages == null ? List.<AiAgentMessage>of() : messages).stream().map(this::toMessage).toList(),
                hasMore && !messages.isEmpty() ? messages.get(0).getSequenceNo() : null,
                hasMore,
                active,
                latest == null ? null : toRun(latest)
        );
    }

    public void archiveSession(AuthenticatedUser user, Long sessionId) {
        sessionService.archive(user, sessionId);
    }

    public AgentRunStatusVO run(AuthenticatedUser user, Long runId) {
        AiAnalysisRun run = runMapper.selectOwnedAgentRun(runId, user.userId());
        if (run == null) throw new BusinessException(ErrorCode.NOT_FOUND, "研究运行不存在");
        return toRun(run);
    }

    @Transactional
    public AgentRunStatusVO cancel(AuthenticatedUser user, Long runId) {
        AiAnalysisRun existing = runMapper.selectOwnedAgentRun(runId, user.userId());
        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND, "研究运行不存在");
        sessionService.lockOwned(user, existing.getSessionId());
        lifecycle.cancel(user, runId);
        return run(user, runId);
    }

    private AgentRunStatusVO toRun(AiAnalysisRun run) {
        AiAgentMessage finalMessage = messageMapper.selectFinalByRun(run.getId());
        List<AiAgentToolCall> calls = toolCallMapper.selectByRunId(run.getId());
        return new AgentRunStatusVO(
                run.getId(), run.getSessionId(), run.getStatus(),
                run.getCurrentStage() == null ? run.getStatus() : run.getCurrentStage(),
                safe(run.getStepCount()), safe(run.getToolCallCount()), run.getVisibleProgress(),
                finalMessage == null ? null : toMessage(finalMessage),
                finalMessage == null ? objectMapper.createArrayNode()
                        : parseJson(finalMessage.getCitationsJson(), false),
                (calls == null ? List.<AiAgentToolCall>of() : calls).stream().map(this::toTool).toList(),
                new AiTokenUsageVO(safe(run.getPromptTokens()), safe(run.getCompletionTokens()), safe(run.getTotalTokens())),
                run.getProvider(), run.getModelId(), run.getPromptVersion(), run.getDiagnosticCode(),
                run.getFinishReason(), run.getProviderRequestId(), run.getLatencyMs(),
                run.getCreatedAt(), run.getCompletedAt()
        );
    }

    private AgentToolCallSummaryVO toTool(AiAgentToolCall call) {
        return new AgentToolCallSummaryVO(
                call.getId(), call.getStepNo(), call.getToolName(), call.getStatus(),
                safe(call.getEvidenceCount()), call.getLatencyMs(), call.getEvidenceHash(), call.getDiagnosticCode()
        );
    }

    private AgentSessionVO toSession(AiAgentSession session) {
        return new AgentSessionVO(
                session.getId(), session.getTitle(), session.getTitleMode(), session.getStatus(),
                parseJson(session.getProfileJson(), true), session.getPinnedAt() != null,
                session.getArchivedAt(), session.getDeletedAt(), session.getPurgeAfter(),
                session.getActiveRunStatus(),
                session.getCreatedAt(), session.getUpdatedAt(), session.getLastMessageAt()
        );
    }

    private AgentMessageVO toMessage(AiAgentMessage message) {
        return new AgentMessageVO(
                message.getId(), message.getRole(), message.getContent(), message.getStatus(),
                message.getSequenceNo(), message.getRunId(), parseJson(message.getCitationsJson(), false),
                message.getCreatedAt()
        );
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

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
