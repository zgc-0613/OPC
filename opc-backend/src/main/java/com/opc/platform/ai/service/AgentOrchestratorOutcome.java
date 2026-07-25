package com.opc.platform.ai.service;

import java.util.List;

public record AgentOrchestratorOutcome(
        String status,
        String answer,
        List<AgentCitation> citations,
        double confidence,
        int modelRounds,
        int toolCallCount,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        String requestId,
        String finishReason
) {
    public AgentOrchestratorOutcome {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
