package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.provider.AiProviderMessage;
import com.opc.platform.ai.provider.AiProviderException;
import com.opc.platform.ai.tool.AgentToolException;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentResearchWorker {

    private final AgentSessionService sessionService;
    private final AgentOrchestrator orchestrator;
    private final AgentRunLifecycleService lifecycle;
    private final AgentRunFinalizer finalizer;
    private final SourceMapper sourceMapper;
    private final ObjectMapper objectMapper;

    public void execute(
            AgentRunLease lease,
            AuthenticatedUser user,
            String profileJson,
            String userMessage
    ) {
        try {
            List<AiProviderMessage> history = history(user, lease.run().getSessionId(), lease.run().getUserMessageId(),
                    lease.config().historyWindow());
            AgentOrchestratorOutcome outcome = orchestrator.execute(
                    new AgentOrchestratorInput(
                            lease.run().getId(), user.userId(), profileJson, userMessage, history,
                            lease.run().getLeaseOwner(), lease.config()
                    ),
                    request -> lifecycle.invoke(lease, request),
                    progress -> lifecycle.updateStage(
                            lease, progress.stage(), progress.modelRound(), progress.toolCallCount())
            );
            String citationsJson = citationJson(outcome.citations());
            finalizer.complete(lease, user, outcome, citationsJson);
        } catch (AgentOrchestratorException exception) {
            lifecycle.fail(lease, terminalStatus(exception), exception.getErrorCode(), exception.getDiagnosticCode());
        } catch (AgentToolException exception) {
            lifecycle.fail(lease, "failed", exception.getErrorCode(), exception.getDiagnosticCode());
        } catch (AiProviderException exception) {
            lifecycle.fail(lease, "failed", exception.getErrorCode(), exception.getDiagnosticCode());
        } catch (BusinessException exception) {
            lifecycle.fail(lease, "failed", exception.getErrorCode(), exception.getErrorCode().name());
        } catch (RuntimeException exception) {
            lifecycle.fail(lease, "failed", ErrorCode.INTERNAL_ERROR, "AGENT_RUNTIME_FAILURE");
        }
    }

    private List<AiProviderMessage> history(
            AuthenticatedUser user,
            Long sessionId,
            Long currentMessageId,
            int limit
    ) {
        List<AiAgentMessage> messages = sessionService.recentMessages(user, sessionId, Math.max(1, limit + 1));
        return messages.stream()
                .filter(message -> !message.getId().equals(currentMessageId))
                .filter(message -> "completed".equals(message.getStatus()))
                .map(message -> "assistant".equals(message.getRole())
                        ? AiProviderMessage.assistant(message.getContent())
                        : AiProviderMessage.user(message.getContent()))
                .toList();
    }

    private String citationJson(List<AgentCitation> citations) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (AgentCitation citation : citations) {
            Source source = sourceMapper.selectById(citation.sourceId());
            if (source == null || !"published".equals(source.getStatus())
                    || !"verified".equals(source.getAiEvidenceStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "研究期间引用来源状态已变化");
            }
            values.add(Map.of(
                    "sourceId", source.getId(),
                    "title", source.getTitle(),
                    "publisher", source.getPublisher() == null ? "" : source.getPublisher(),
                    "url", source.getUrl(),
                    "claim", citation.claim()
            ));
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "引用摘要无法序列化");
        }
    }

    private String terminalStatus(AgentOrchestratorException exception) {
        return "AGENT_TIMEOUT".equals(exception.getDiagnosticCode()) ? "expired" : "failed";
    }
}
