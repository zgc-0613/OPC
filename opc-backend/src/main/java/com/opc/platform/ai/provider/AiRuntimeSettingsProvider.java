package com.opc.platform.ai.provider;

public interface AiRuntimeSettingsProvider {
    AiRuntimeSettings current();
    long dailyTokenQuota();

    default AiRuntimeSnapshot snapshot() {
        return new AiRuntimeSnapshot(current(), dailyTokenQuota());
    }
}
