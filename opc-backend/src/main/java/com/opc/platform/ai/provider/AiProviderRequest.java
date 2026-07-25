package com.opc.platform.ai.provider;

public record AiProviderRequest(
        String capability,
        String promptVersion,
        String systemPrompt,
        String userPrompt,
        String responseSchema,
        java.util.List<AiProviderMessage> messages,
        java.util.List<AiToolDefinition> tools,
        boolean jsonResponse
) {
    public AiProviderRequest {
        messages = messages == null ? java.util.List.of() : java.util.List.copyOf(messages);
        tools = tools == null ? java.util.List.of() : java.util.List.copyOf(tools);
    }

    public AiProviderRequest(
            String capability,
            String promptVersion,
            String systemPrompt,
            String userPrompt,
            String responseSchema
    ) {
        this(capability, promptVersion, systemPrompt, userPrompt, responseSchema,
                java.util.List.of(), java.util.List.of(), true);
    }
}
