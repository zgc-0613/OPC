package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;

/** Immutable, session-scoped research boundary for Phase Three starts. */
public record PhaseThreeTaskContext(
        String taskType,
        JsonNode node,
        String canonicalJson,
        String hash
) {
}
