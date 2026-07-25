package com.opc.platform.ai.provider;

public record AiProviderResponse(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs,
        String requestId,
        String finishReason
) {
    public AiProviderResponse(String content) {
        this(content, 0, 0, 0, 0, null, "stop");
    }

    public AiProviderResponse(
            String content,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long latencyMs,
            String requestId
    ) {
        this(content, promptTokens, completionTokens, totalTokens, latencyMs, requestId, "stop");
    }
}
