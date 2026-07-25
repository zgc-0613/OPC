package com.opc.platform.ai.vo;

import java.time.LocalDateTime;

public record AgentUsageVO(
        long usedTokens,
        long limitTokens,
        long remainingTokens,
        boolean unlimited,
        LocalDateTime resetAt
) {
}
