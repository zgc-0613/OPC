package com.opc.platform.ai.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record AgentSessionVO(
        Long sessionId,
        String title,
        String titleMode,
        String status,
        JsonNode profile,
        boolean pinned,
        LocalDateTime archivedAt,
        LocalDateTime deletedAt,
        LocalDateTime purgeAfter,
        String activeRunStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastMessageAt
) {
}
