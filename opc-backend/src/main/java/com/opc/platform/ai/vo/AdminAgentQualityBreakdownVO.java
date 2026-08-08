package com.opc.platform.ai.vo;

public record AdminAgentQualityBreakdownVO(
        String key,
        Long sampleSize,
        Long completedCount,
        Long failedCount,
        Long evidenceInsufficientCount
) {
}
