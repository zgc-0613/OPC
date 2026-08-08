package com.opc.platform.ai.vo;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentResearchBranchMaterialVO(
        Long sourceSessionId,
        Long sourceRunId,
        String requestedIntent,
        JsonNode taskContext,
        String taskContextVersion,
        String taskContextHash,
        String resultSummary,
        JsonNode citations,
        String evidenceVersion
) {
}
