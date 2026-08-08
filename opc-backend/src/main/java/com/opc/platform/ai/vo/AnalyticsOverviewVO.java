package com.opc.platform.ai.vo;

import java.time.LocalDateTime;
import java.util.List;

public record AnalyticsOverviewVO(
        String dataVersion,
        LocalDateTime generatedAt,
        List<MetricCard> cards,
        String status
) {
    public record MetricCard(String metricId, String label, Long value, String unit, String readiness, String caveat) {}
}
