package com.opc.platform.ai.service;

import com.opc.platform.ai.vo.AgentSessionVO;

public record AgentResearchStartReceipt(
        AgentSessionVO session,
        Long messageId,
        Long runId,
        String status
) {
}
