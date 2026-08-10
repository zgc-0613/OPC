package com.opc.platform.ai.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AnalyticsUnavailableVO(
        List<Object> data,
        Metric metric,
        Map<String, Object> filters,
        long sampleSize,
        long missingCount,
        long totalEligible,
        OffsetDateTime generatedAt,
        String dataVersion,
        Freshness freshness,
        List<Caveat> caveats,
        Drilldown drilldown,
        String status,
        boolean available,
        String unavailableReason,
        Freshness dataFreshness,
        boolean verifiedOnly,
        Coverage coverage,
        List<Object> rows,
        List<Object> series,
        String nextCursor,
        boolean hasMore,
        Long aggregateValue
) {
    public AnalyticsUnavailableVO(
            List<Object> data,
            Metric metric,
            Map<String, Object> filters,
            long sampleSize,
            long missingCount,
            long totalEligible,
            OffsetDateTime generatedAt,
            String dataVersion,
            Freshness freshness,
            List<Caveat> caveats,
            Drilldown drilldown,
            String status
    ) {
        this(
                data, metric, filters, sampleSize, missingCount, totalEligible,
                generatedAt, dataVersion, freshness, caveats, drilldown, status,
                false, firstCaveatCode(caveats), freshness, true,
                Coverage.from(totalEligible, sampleSize, missingCount),
                List.of(), List.of(), null, false, null);
    }

    private static String firstCaveatCode(List<Caveat> caveats) {
        return caveats == null || caveats.isEmpty() ? "ANALYTICS_METRIC_NOT_READY" : caveats.get(0).code();
    }

    public record Metric(
            String metricId,
            String name,
            String version,
            String readiness,
            String definition,
            String unit,
            boolean multiLabel
    ) {}

    public record Freshness(
            OffsetDateTime lastEligibleUpdateAt,
            OffsetDateTime reviewWatermarkAt,
            long ageSeconds,
            String status
    ) {}

    public record Caveat(String code, String message, String severity, List<String> affectedFields) {}

    public record Drilldown(String href, List<String> entityTypes, String cursor, boolean available) {}

    public record Coverage(long eligible, long covered, long missing, BigDecimal ratio) {
        public static Coverage from(long eligible, long covered, long missing) {
            BigDecimal ratio = eligible <= 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(covered).divide(
                            BigDecimal.valueOf(eligible), 6, RoundingMode.HALF_UP);
            return new Coverage(eligible, covered, missing, ratio);
        }
    }

    public record TrendPoint(
            String bucketId,
            java.time.LocalDate periodStart,
            java.time.LocalDate periodEndExclusive,
            long value,
            long sampleSize,
            long missingCount,
            boolean syntheticEmptyBucket
    ) {}

    public record RegionRow(
            String bucketId,
            Long regionId,
            Long parentId,
            String level,
            String regionRole,
            String label,
            long value,
            BigDecimal ratio,
            long sampleSize,
            String readiness,
            Drilldown drilldown
    ) {}

    public record PolicyDrilldownRow(
            Long id,
            String title,
            java.time.LocalDate publishDate,
            Long regionId,
            String regionName,
            String issuingBody,
            String policyType,
            String evidenceStatus,
            String detailHref
    ) {}
}
