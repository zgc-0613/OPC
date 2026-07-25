package com.opc.platform.ai.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record AgentSessionVO(
        Long sessionId,
        String title,
        String status,
        JsonNode profile,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastMessageAt
) {
}
