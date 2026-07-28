package com.opc.platform.ai.service;

public record AgentSubmissionIdentity(
        String kind,
        String contentHash,
        String profileHash,
        long sessionContentGeneration,
        String requestedIntent
) {
    public AgentSubmissionIdentity(
            String kind,
            String contentHash,
            String profileHash,
            long sessionContentGeneration
    ) {
        this(kind, contentHash, profileHash, sessionContentGeneration, "auto");
    }
}
