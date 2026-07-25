package com.opc.platform.ai.provider;

import java.util.List;

public record AiProviderMessage(
        String role,
        String content,
        String toolCallId,
        List<AiProviderToolCall> toolCalls
) {
    public AiProviderMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static AiProviderMessage system(String content) {
        return new AiProviderMessage("system", content, null, List.of());
    }

    public static AiProviderMessage user(String content) {
        return new AiProviderMessage("user", content, null, List.of());
    }

    public static AiProviderMessage assistant(String content) {
        return new AiProviderMessage("assistant", content, null, List.of());
    }

    public static AiProviderMessage assistantToolCalls(List<AiProviderToolCall> toolCalls) {
        return new AiProviderMessage("assistant", null, null, toolCalls);
    }

    public static AiProviderMessage tool(String toolCallId, String content) {
        return new AiProviderMessage("tool", content, toolCallId, List.of());
    }
}
