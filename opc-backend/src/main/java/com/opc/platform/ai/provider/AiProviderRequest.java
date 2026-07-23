package com.opc.platform.ai.provider;

public record AiProviderRequest(
        String capability,
        String promptVersion,
        String systemPrompt,
        String userPrompt,
        String responseSchema
) {
}
