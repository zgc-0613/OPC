package com.opc.platform.ai.service;

import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AiProviderMessage;

import java.util.List;

public record AgentOrchestratorInput(
        Long runId,
        Long userId,
        String profileJson,
        String userMessage,
        List<AiProviderMessage> history,
        String leaseOwner,
        Integer executionAttempt,
        AgentRuntimeConfig config,
        String requestedIntent,
        String taskContextJson
) {
    public AgentOrchestratorInput {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public AgentOrchestratorInput(
            Long runId,
            Long userId,
            String profileJson,
            String userMessage,
            List<AiProviderMessage> history,
            String leaseOwner,
            AgentRuntimeConfig config,
            String requestedIntent,
            String taskContextJson
    ) {
        this(runId, userId, profileJson, userMessage, history, leaseOwner, null,
                config, requestedIntent, taskContextJson);
    }

    public AgentOrchestratorInput(
            Long runId,
            Long userId,
            String profileJson,
            String userMessage,
            List<AiProviderMessage> history,
            String leaseOwner,
            AgentRuntimeConfig config,
            String requestedIntent
    ) {
        this(runId, userId, profileJson, userMessage, history, leaseOwner, null, config, requestedIntent, null);
    }

    public AgentOrchestratorInput(
            Long runId,
            Long userId,
            String profileJson,
            String userMessage,
            List<AiProviderMessage> history,
            AgentRuntimeConfig config
    ) {
        this(runId, userId, profileJson, userMessage, history, null, null, config, "auto", null);
    }

    public AgentOrchestratorInput(
            Long runId,
            Long userId,
            String profileJson,
            String userMessage,
            List<AiProviderMessage> history,
            String leaseOwner,
            AgentRuntimeConfig config
    ) {
        this(runId, userId, profileJson, userMessage, history, leaseOwner, null, config, "auto", null);
    }
}
