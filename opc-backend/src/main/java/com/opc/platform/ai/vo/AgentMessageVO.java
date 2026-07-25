package com.opc.platform.ai.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record AgentMessageVO(
        Long messageId,
        String role,
        String content,
        String status,
        Integer sequenceNo,
        Long runId,
        JsonNode citations,
        LocalDateTime createdAt
) {
}
