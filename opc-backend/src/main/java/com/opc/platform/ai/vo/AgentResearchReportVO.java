package com.opc.platform.ai.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record AgentResearchReportVO(
        Long reportId, Long userId, Long sessionId, Long runId, Long finalMessageId,
        String title, String notes, JsonNode result, JsonNode citationManifest,
        String evidenceVersion, String dataVersion, Boolean sourceSessionAvailable,
        String status, Long revision, LocalDateTime trashedAt, LocalDateTime purgeAfter,
        LocalDateTime createdAt, LocalDateTime updatedAt, String evidenceState
) {}
