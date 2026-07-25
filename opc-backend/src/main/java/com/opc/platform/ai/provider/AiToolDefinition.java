package com.opc.platform.ai.provider;

public record AiToolDefinition(
        String name,
        String description,
        String parametersJson
) {
}
