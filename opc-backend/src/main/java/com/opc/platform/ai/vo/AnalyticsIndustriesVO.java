package com.opc.platform.ai.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("available")
    public boolean available() {
        return !"unavailable".equalsIgnoreCase(status) && buckets != null && !buckets.isEmpty();
    }

    @JsonProperty("unavailableReason")
    public String unavailableReason() {
        if (available()) return null;
        return caveats == null || caveats.isEmpty()
                ? "ANALYTICS_METRIC_NOT_READY"
                : caveats.get(0).code();
    }

    @JsonProperty("dataFreshness")
    public AnalyticsUnavailableVO.Freshness dataFreshness() {
        if (freshness == null) return new AnalyticsUnavailableVO.Freshness(null, null, -1, "unknown");
        return new AnalyticsUnavailableVO.Freshness(
                freshness.lastEligibleUpdateAt(), freshness.reviewWatermarkAt(),
                freshness.ageSeconds(), freshness.status());
    }

    @JsonProperty("verifiedOnly")
    public boolean verifiedOnly() {
        return true;
    }

    @JsonProperty("coverage")
    public AnalyticsUnavailableVO.Coverage coverage() {
        return AnalyticsUnavailableVO.Coverage.from(totalEligible, sampleSize, missingCount);
    }

    @JsonProperty("rows")
    public List<Bucket> rows() {
        return buckets == null ? List.of() : buckets;
    }

    @JsonProperty("series")
    public List<Object> series() {
        return List.of();
    }

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
