package com.opc.platform.ai.service;

public record AgentClarificationDecision(
        String contextJson,
        String question,
        boolean evidenceInsufficient
) {
}
