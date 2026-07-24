package com.opc.platform.ai.provider;

public record AiRuntimeSnapshot(
        AiRuntimeSettings settings,
        long dailyTokenQuota
) {
}
