package com.opc.platform.ai.provider;

public record AiProviderRequest(
        String capability,
        String promptVersion,
        String systemPrompt,
        String userPrompt,
        String responseSchema,
        java.util.List<AiProviderMessage> messages,
        java.util.List<AiToolDefinition> tools,
        boolean jsonResponse,
        Integer maxOutputTokens
) {
    public AiProviderRequest {
        messages = messages == null ? java.util.List.of() : java.util.List.copyOf(messages);
        tools = tools == null ? java.util.List.of() : java.util.List.copyOf(tools);
        if (maxOutputTokens != null && (maxOutputTokens < 1 || maxOutputTokens > 4096)) {
            throw new IllegalArgumentException("Request output token budget must be between 1 and 4096");
        }
    }

    public AiProviderRequest(
            String capability,
            String promptVersion,
            String systemPrompt,
            String userPrompt,
            String responseSchema,
            java.util.List<AiProviderMessage> messages,
            java.util.List<AiToolDefinition> tools,
            boolean jsonResponse
    ) {
        this(capability, promptVersion, systemPrompt, userPrompt, responseSchema,
                messages, tools, jsonResponse, null);
    }

    public AiProviderRequest(
            String capability,
            String promptVersion,
            String systemPrompt,
            String userPrompt,
            String responseSchema
    ) {
        this(capability, promptVersion, systemPrompt, userPrompt, responseSchema,
                java.util.List.of(), java.util.List.of(), true, null);
    }
}
