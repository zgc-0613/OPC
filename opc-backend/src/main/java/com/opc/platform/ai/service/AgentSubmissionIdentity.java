package com.opc.platform.ai.service;

public record AgentSubmissionIdentity(
        String kind,
        String contentHash,
        String profileHash,
        long sessionContentGeneration
) {
}
