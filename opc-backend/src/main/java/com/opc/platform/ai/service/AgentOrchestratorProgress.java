package com.opc.platform.ai.service;

public record AgentOrchestratorProgress(
        String stage,
        int modelRound,
        int toolCallCount
) {
}
