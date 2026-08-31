package com.opc.platform.ai.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

public record AnalyticsOverviewVO(
        String dataVersion,
        LocalDateTime generatedAt,
        List<MetricCard> cards,
        String status,
        List<MaterialCount> materialCounts
) {
    @JsonProperty("available")
    public boolean available() {
        return cards != null && cards.stream().anyMatch(card -> card != null
                && "Green".equalsIgnoreCase(card.readiness())
                && card.value() != null);
    }

    @JsonProperty("unavailableReason")
    public String unavailableReason() {
        if (available() || cards == null) return null;
        return cards.stream().filter(card -> card != null && card.caveat() != null && !card.caveat().isBlank())
                .map(MetricCard::metricId)
                .map(metricId -> switch (metricId) {
                    case "overview.verified_cases" -> "CANONICAL_CASE_DEDUPLICATION_NOT_READY";
                    case "overview.verified_sources" -> "CANONICAL_SOURCE_DEDUPLICATION_NOT_READY";
                    case "overview.covered_technologies" -> "TECHNOLOGY_TAXONOMY_NOT_READY";
                    default -> "ANALYTICS_METRIC_NOT_READY";
                })
                .findFirst().orElse("ANALYTICS_METRIC_NOT_READY");
    }

    @JsonProperty("dataFreshness")
    public AnalyticsUnavailableVO.Freshness dataFreshness() {
        if (generatedAt == null) return new AnalyticsUnavailableVO.Freshness(null, null, -1, "unknown");
        OffsetDateTime timestamp = generatedAt.atOffset(ZoneOffset.ofHours(8));
        return new AnalyticsUnavailableVO.Freshness(timestamp, timestamp, 0, "current");
    }

    @JsonProperty("verifiedOnly")
    public boolean verifiedOnly() {
        return true;
    }

    @JsonProperty("coverage")
    public AnalyticsUnavailableVO.Coverage coverage() {
        // Overview cards intentionally do not aggregate heterogeneous records.
        return AnalyticsUnavailableVO.Coverage.from(0, 0, 0);
    }

    @JsonProperty("filters")
    public Map<String, Object> filters() {
        return Map.of();
    }

    @JsonProperty("rows")
    public List<MetricCard> rows() {
        return List.of();
    }

    @JsonProperty("series")
    public List<Object> series() {
        return List.of();
    }

    public record MetricCard(String metricId, String label, Long value, String unit, String readiness, String caveat) {}

    public record MaterialCount(String code, String label, Long value) {}
}
