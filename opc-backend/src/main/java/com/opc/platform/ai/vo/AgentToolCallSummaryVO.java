package com.opc.platform.ai.vo;

public record AgentToolCallSummaryVO(
        Long toolCallId,
        Integer stepNo,
        String toolName,
        String status,
        Integer evidenceCount,
        Long latencyMs,
        String evidenceHash,
        String diagnosticCode
) {
}
