package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;

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
        String finishReason,
        JsonNode structuredResult,
        String diagnosticCode
) {
    public AgentOrchestratorOutcome(
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
            String finishReason,
            JsonNode structuredResult
    ) {
        this(status, answer, citations, confidence, modelRounds, toolCallCount,
                promptTokens, completionTokens, totalTokens, latencyMs, requestId,
                finishReason, structuredResult, null);
    }

    public AgentOrchestratorOutcome {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
