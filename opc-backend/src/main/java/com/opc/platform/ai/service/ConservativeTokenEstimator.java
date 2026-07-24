package com.opc.platform.ai.service;

import com.opc.platform.ai.provider.AiProviderRequest;

import java.nio.charset.StandardCharsets;

public final class ConservativeTokenEstimator {

    private static final int MESSAGE_OVERHEAD = 24;

    private ConservativeTokenEstimator() {
    }

    public static int estimate(AiProviderRequest request) {
        String content = String.join("\n",
                safe(request.capability()),
                safe(request.promptVersion()),
                safe(request.systemPrompt()),
                safe(request.userPrompt()),
                safe(request.responseSchema())
        );
        long tokens = MESSAGE_OVERHEAD + content.getBytes(StandardCharsets.UTF_8).length;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, tokens));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
