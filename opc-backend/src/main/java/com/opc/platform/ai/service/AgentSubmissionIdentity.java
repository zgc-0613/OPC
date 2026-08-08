package com.opc.platform.ai.service;

public record AgentSubmissionIdentity(
        String kind,
        String contentHash,
        String profileHash,
        long sessionContentGeneration,
        String requestedIntent,
        String taskContextVersion,
        String taskContextJson,
        String taskContextHash,
        AgentAnalyticsSnapshotBinding analyticsSnapshot
) {
    public AgentSubmissionIdentity(
            String kind,
            String contentHash,
            String profileHash,
            long sessionContentGeneration
    ) {
        this(kind, contentHash, profileHash, sessionContentGeneration, "auto", null, null, null, null);
    }

    public AgentSubmissionIdentity(
            String kind,
            String contentHash,
            String profileHash,
            long sessionContentGeneration,
            String requestedIntent
    ) {
        this(kind, contentHash, profileHash, sessionContentGeneration, requestedIntent, null, null, null, null);
    }

    public AgentSubmissionIdentity(
            String kind,
            String contentHash,
            String profileHash,
            long sessionContentGeneration,
            String requestedIntent,
            String taskContextVersion,
            String taskContextJson,
            String taskContextHash
    ) {
        this(kind, contentHash, profileHash, sessionContentGeneration, requestedIntent,
                taskContextVersion, taskContextJson, taskContextHash, null);
    }

    public AgentSubmissionIdentity withTaskContext(String version, String json, String hash) {
        return new AgentSubmissionIdentity(
                kind, contentHash, profileHash, sessionContentGeneration, requestedIntent,
                version, json, hash, analyticsSnapshot);
    }

    public AgentSubmissionIdentity withAnalyticsSnapshot(AgentAnalyticsSnapshotBinding binding) {
        return new AgentSubmissionIdentity(
                kind, contentHash, profileHash, sessionContentGeneration, requestedIntent,
                taskContextVersion, taskContextJson, taskContextHash, binding);
    }
}
