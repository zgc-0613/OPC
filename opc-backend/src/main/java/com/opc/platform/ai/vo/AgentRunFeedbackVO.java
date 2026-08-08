package com.opc.platform.ai.vo;

import java.time.LocalDateTime;

public record AgentRunFeedbackVO(
        Long runId,
        String rating,
        String reason,
        String comment,
        Long revision,
        LocalDateTime updatedAt
) {
}
