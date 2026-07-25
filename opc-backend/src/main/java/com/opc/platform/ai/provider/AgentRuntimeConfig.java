package com.opc.platform.ai.provider;

import java.time.Duration;

public record AgentRuntimeConfig(
        boolean enabled,
        int maxModelRounds,
        int maxToolCalls,
        int maxTokens,
        int historyWindow,
        Duration timeout,
        String toolMode
) {
}
