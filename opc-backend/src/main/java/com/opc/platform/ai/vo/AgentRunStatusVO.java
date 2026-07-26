package com.opc.platform.ai.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public record AgentRunStatusVO(
        Long runId,
        Long sessionId,
        String status,
        String retryContent,
        String currentStage,
        Integer stepCount,
        Integer toolCallCount,
        String visibleProgress,
        AgentMessageVO finalMessage,
        JsonNode citations,
        List<AgentToolCallSummaryVO> tools,
        AiTokenUsageVO tokenUsage,
        String provider,
        String model,
        String promptVersion,
        String diagnosticCode,
        String finishReason,
        String requestId,
        Long latencyMs,
        JsonNode structuredResult,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
