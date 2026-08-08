package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.opc.platform.ai.vo.AgentSessionVO;

public record AgentResearchStartReceipt(
        AgentSessionVO session,
        Long messageId,
        Long runId,
        String status,
        String taskType,
        String taskContextHash,
        JsonNode taskContext
) {
    public AgentResearchStartReceipt(
            AgentSessionVO session, Long messageId, Long runId, String status
    ) {
        this(session, messageId, runId, status, null, null, null);
    }
}
