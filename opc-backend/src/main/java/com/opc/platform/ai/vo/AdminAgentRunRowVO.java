package com.opc.platform.ai.vo;

import java.time.LocalDateTime;

public record AdminAgentRunRowVO(
        Long runId,
        String maskedUser,
        Long sessionId,
        String status,
        String provider,
        String model,
        Integer modelRounds,
        Integer toolCallCount,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long latencyMs,
        String finishReason,
        String diagnosticCode,
        String requestId,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
