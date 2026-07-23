package com.opc.platform.ai.provider;

public interface AiRuntimeSettingsProvider {
    AiRuntimeSettings current();
    long dailyTokenQuota();
}
