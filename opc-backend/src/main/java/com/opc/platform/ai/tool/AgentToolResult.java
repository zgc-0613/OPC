package com.opc.platform.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

public record AgentToolResult(
        JsonNode output,
        int evidenceCount,
        String evidenceHash,
        Set<Long> sourceIds,
        Set<Long> caseIds
) {
    public AgentToolResult {
        sourceIds = sourceIds == null ? Set.of() : Set.copyOf(sourceIds);
        caseIds = caseIds == null ? Set.of() : Set.copyOf(caseIds);
    }
}
