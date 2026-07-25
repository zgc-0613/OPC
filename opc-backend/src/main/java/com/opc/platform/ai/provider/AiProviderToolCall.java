package com.opc.platform.ai.provider;

public record AiProviderToolCall(
        String id,
        String name,
        String argumentsJson
) {
}
