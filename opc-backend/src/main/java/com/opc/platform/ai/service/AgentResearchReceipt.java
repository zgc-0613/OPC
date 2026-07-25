package com.opc.platform.ai.service;

public record AgentResearchReceipt(
        Long sessionId,
        Long messageId,
        Long runId,
        String status
) {
}
