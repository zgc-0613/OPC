package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiRuntimeSettings;

public final class AgentRunLease {

    private final AiAnalysisRun run;
    private final AiRuntimeSettings runtime;
    private final AiProviderDescriptor descriptor;
    private final AgentRuntimeConfig config;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private long latencyMs;
    private String requestId;
    private String finishReason;
    private int modelRounds;
    private int toolCallCount;
    private int providerCallCount;

    AgentRunLease(
            AiAnalysisRun run,
            AiRuntimeSettings runtime,
            AiProviderDescriptor descriptor,
            AgentRuntimeConfig config
    ) {
        this.run = run;
        this.runtime = runtime;
        this.descriptor = descriptor;
        this.config = config;
    }

    void add(com.opc.platform.ai.provider.AiProviderResponse response) {
        promptTokens += Math.max(0, response.promptTokens());
        completionTokens += Math.max(0, response.completionTokens());
        totalTokens += Math.max(Math.max(0, response.totalTokens()),
                Math.max(0, response.promptTokens()) + Math.max(0, response.completionTokens()));
        latencyMs += Math.max(0, response.latencyMs());
        requestId = response.requestId();
        finishReason = response.finishReason();
    }

    void updateProgress(int modelRounds, int toolCallCount) {
        this.modelRounds = Math.max(this.modelRounds, Math.max(0, modelRounds));
        this.toolCallCount = Math.max(this.toolCallCount, Math.max(0, toolCallCount));
    }

    int nextProviderCall() { return ++providerCallCount; }

    public AiAnalysisRun run() { return run; }
    public AiRuntimeSettings runtime() { return runtime; }
    public AiProviderDescriptor descriptor() { return descriptor; }
    public AgentRuntimeConfig config() { return config; }
    public int promptTokens() { return promptTokens; }
    public int completionTokens() { return completionTokens; }
    public int totalTokens() { return totalTokens; }
    public long latencyMs() { return latencyMs; }
    public String requestId() { return requestId; }
    public String finishReason() { return finishReason; }
    public int modelRounds() { return modelRounds; }
    public int toolCallCount() { return toolCallCount; }
}
