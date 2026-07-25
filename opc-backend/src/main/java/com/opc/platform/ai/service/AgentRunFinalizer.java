package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentRunFinalizer {

    private final AiAnalysisRunMapper runMapper;
    private final AgentSessionService sessionService;
    private final AgentRunLifecycleService lifecycle;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiAgentMessage complete(
            AgentRunLease lease,
            AuthenticatedUser user,
            AgentOrchestratorOutcome outcome,
            String citationsJson
    ) {
        AiAnalysisRun locked = runMapper.selectRunForUpdate(lease.run().getId());
        if (locked == null || !"running".equals(locked.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究运行已取消、失败或过期，迟到结果已丢弃");
        }
        AiAgentMessage message = sessionService.appendMessage(
                user, locked.getSessionId(), "assistant", outcome.answer(), "completed", locked.getId(), citationsJson
        );
        lifecycle.complete(lease, outcome, safeResultJson(message.getId(), outcome.citations().size()));
        return message;
    }

    private String safeResultJson(Long finalMessageId, int citationCount) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "finalMessageId", finalMessageId,
                    "citationCount", citationCount
            ));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "研究结果审计摘要无法序列化");
        }
    }
}
