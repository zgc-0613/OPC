package com.opc.platform.ai.provider;

import java.time.Duration;

public record AiRuntimeSettings(
        String provider,
        String apiFormat,
        String apiBaseUrl,
        String model,
        String apiKey,
        double temperature,
        int maxOutputTokens,
        Duration timeout,
        int retryCount,
        boolean enabled
) {
}
