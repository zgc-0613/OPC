package com.opc.platform.ai.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AnalyticsIndustriesVO(
        List<Bucket> buckets,
        Metric metric,
        Filters filters,
        long sampleSize,
        long missingCount,
        long totalEligible,
        OffsetDateTime generatedAt,
        String dataVersion,
        Freshness freshness,
        List<Caveat> caveats,
        String status
) {
    public record Bucket(
            String bucketId,
            Long industryTagId,
            String label,
            long value,
            BigDecimal ratio,
            long sampleSize,
            long missingCount,
            String readiness,
            Drilldown drilldown
    ) {}

    public record Metric(
            String metricId,
            String name,
            String version,
            String readiness,
            String definition,
            String unit,
            boolean multiLabel
    ) {}

    public record Filters(List<Long> industryTagIds) {}

    public record Freshness(
            OffsetDateTime lastEligibleUpdateAt,
            OffsetDateTime reviewWatermarkAt,
            long ageSeconds,
            String status
    ) {}

    public record Caveat(String code, String message, String severity, List<String> affectedFields) {}

    public record Drilldown(String href, List<String> entityTypes, boolean available) {}
}
