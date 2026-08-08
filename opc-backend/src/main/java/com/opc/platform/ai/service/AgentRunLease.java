package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiRuntimeSettings;

import java.time.Duration;
import java.time.LocalDateTime;

public final class AgentRunLease {

    private final AiAnalysisRun run;
    private final AiRuntimeSettings runtime;
    private final AiProviderDescriptor descriptor;
    private final AgentRuntimeConfig config;
    private final String leaseOwner;
    private final int executionAttempt;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private long latencyMs;
    private String requestId;
    private String finishReason;
    private int modelRounds;
    private int toolCallCount;
    private int providerCallCount;
    private volatile boolean leaseLost;

    AgentRunLease(
            AiAnalysisRun run,
            AiRuntimeSettings runtime,
            AiProviderDescriptor descriptor,
            AgentRuntimeConfig config
    ) {
        this(run, runtime, descriptor, config, 0);
    }

    AgentRunLease(
            AiAnalysisRun run,
            AiRuntimeSettings runtime,
            AiProviderDescriptor descriptor,
            AgentRuntimeConfig config,
            int existingProviderCalls
    ) {
        this.run = run;
        this.runtime = runtime;
        this.descriptor = descriptor;
        this.config = config;
        this.leaseOwner = run == null ? null : run.getLeaseOwner();
        this.executionAttempt = run == null || run.getExecutionAttempts() == null
                ? 0 : Math.max(0, run.getExecutionAttempts());
        this.providerCallCount = Math.max(0, existingProviderCalls);
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

    public boolean isCurrentAt(LocalDateTime now) {
        return !leaseLost
                && "running".equals(run.getStatus())
                && run.getLeaseOwner() != null
                && leaseOwner != null
                && leaseOwner.equals(run.getLeaseOwner())
                && run.getExecutionAttempts() != null
                && run.getExecutionAttempts() == executionAttempt
                && (run.getLeaseExpiresAt() == null || !now.isAfter(run.getLeaseExpiresAt()))
                && (run.getDeadlineAt() == null || !now.isAfter(run.getDeadlineAt()));
    }

    public void markLeaseLost() { leaseLost = true; }
    public boolean leaseLost() { return leaseLost; }
    public String owner() { return leaseOwner; }

    public Duration leaseWindow() {
        if (run.getHeartbeatAt() != null && run.getLeaseExpiresAt() != null
                && run.getLeaseExpiresAt().isAfter(run.getHeartbeatAt())) {
            return Duration.between(run.getHeartbeatAt(), run.getLeaseExpiresAt());
        }
        return config == null || config.timeout() == null
                ? Duration.ofSeconds(45) : config.timeout();
    }

    public boolean deadlineReached(LocalDateTime now) {
        return run.getDeadlineAt() != null && !run.getDeadlineAt().isAfter(now);
    }

    public AiAnalysisRun run() { return run; }
    public AiRuntimeSettings runtime() { return runtime; }
    public AiProviderDescriptor descriptor() { return descriptor; }
    public AgentRuntimeConfig config() { return config; }
    public String leaseOwner() { return leaseOwner; }
    public int executionAttempt() { return executionAttempt; }
    public int promptTokens() { return promptTokens; }
    public int completionTokens() { return completionTokens; }
    public int totalTokens() { return totalTokens; }
    public long latencyMs() { return latencyMs; }
    public String requestId() { return requestId; }
    public String finishReason() { return finishReason; }
    public int modelRounds() { return modelRounds; }
    public int toolCallCount() { return toolCallCount; }
}
