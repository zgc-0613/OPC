package com.opc.platform.ai.provider;

public record AiProviderResponse(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        String requestId,
        String finishReason,
        java.util.List<AiProviderToolCall> toolCalls
) {
    public AiProviderResponse {
        toolCalls = toolCalls == null ? java.util.List.of() : java.util.List.copyOf(toolCalls);
    }

    public AiProviderResponse(String content) {
        this(content, 0, 0, 0, 0, null, "stop", java.util.List.of());
    }

    public AiProviderResponse(
            String content,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long latencyMs,
            String requestId
    ) {
        this(content, promptTokens, completionTokens, totalTokens, latencyMs, requestId, "stop", java.util.List.of());
    }

    public AiProviderResponse(
            String content,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long latencyMs,
            String requestId,
            String finishReason
    ) {
        this(content, promptTokens, completionTokens, totalTokens, latencyMs, requestId, finishReason,
                java.util.List.of());
    }
}
