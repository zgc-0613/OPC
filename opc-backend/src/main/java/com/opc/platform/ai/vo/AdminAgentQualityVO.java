package com.opc.platform.ai.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AdminAgentQualityVO(
        Long sampleSize,
        Long completedCount,
        Long failedCount,
        Long cancelledCount,
        Long timeoutCount,
        Long evidenceInsufficientCount,
        Long helpfulCount,
        Long notHelpfulCount,
        Double helpfulRate,
        Map<String, Long> reasonCounts,
        Map<String, Long> evidenceInsufficientReasons,
        Map<String, Long> failureReasons,
        List<AdminAgentQualityBreakdownVO> taskBreakdown,
        List<AdminAgentQualityBreakdownVO> modelBreakdown,
        AdminAgentQualitySummaryVO latencySummary,
        AdminAgentQualitySummaryVO tokenSummary,
        AdminAgentQualitySummaryVO toolCallSummary,
        String granularity,
        LocalDateTime generatedAt
) {
}
