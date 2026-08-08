package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.vo.AnalyticsIndustriesVO;
import com.opc.platform.ai.vo.AnalyticsOverviewVO;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashSet;

@Service
@RequiredArgsConstructor
public class AnalyticsOverviewService {
    private static final ZoneId ANALYTICS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String INDUSTRY_CASE_COUNT = "industry.case_count";
    private static final Set<String> INDUSTRY_METRICS = Set.of(
            INDUSTRY_CASE_COUNT, "industry.case_share", "industry.new_case_trend",
            "industry.region_distribution", "industry.related_policy_count");
    private final CaseItemMapper caseMapper;
    private final PolicyMapper policyMapper;
    private final SourceMapper sourceMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AnalyticsOverviewVO overview(AuthenticatedUser user) {
        if (user == null || user.userId() == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        long cases = count("case");
        long policies = count("policy");
        long sources = count("source");
        String version = version(cases, policies, sources);
        return new AnalyticsOverviewVO(version, LocalDateTime.now(), List.of(
                new AnalyticsOverviewVO.MetricCard("overview.verified_cases", "已核验案例", cases, "条", "green", null),
                new AnalyticsOverviewVO.MetricCard("overview.verified_policies", "已核验政策", policies, "条", "green", null),
                new AnalyticsOverviewVO.MetricCard("overview.verified_sources", "已核验来源", sources, "条", "green", null),
                new AnalyticsOverviewVO.MetricCard("technology.coverage", "技术 taxonomy", null, "", "red", "正式技术 taxonomy 尚未完成审核")
        ), "complete");
    }

    @Transactional(readOnly = true)
    public AnalyticsIndustriesVO industries(
            AuthenticatedUser user,
            String metricId,
            List<Long> industryTagIds
    ) {
        if (user == null || user.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return buildIndustries(metricId, normalizeIndustryTagIds(industryTagIds));
    }

    private AnalyticsIndustriesVO buildIndustries(String requestedMetricId, List<Long> tagIds) {
        String metricId = requestedMetricId == null || requestedMetricId.isBlank()
                ? INDUSTRY_CASE_COUNT : requestedMetricId.trim();
        if (!INDUSTRY_METRICS.contains(metricId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "ANALYTICS_METRIC_NOT_FOUND");
        }
        if (!tagIds.isEmpty()) {
            List<Long> approved = caseMapper.selectApprovedIndustryTagIds(tagIds);
            if (approved == null || !approved.equals(tagIds)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
            }
        }

        long cases = count("case");
        long policies = count("policy");
        long sources = count("source");
        String dataVersion = version(cases, policies, sources);
        OffsetDateTime generatedAt = OffsetDateTime.now(ANALYTICS_ZONE);
        AnalyticsIndustriesVO.Filters filters = new AnalyticsIndustriesVO.Filters(tagIds);
        if (!INDUSTRY_CASE_COUNT.equals(metricId)) {
            long totalForMetric = "industry.related_policy_count".equals(metricId) ? policies : cases;
            return unavailableIndustryMetric(metricId, filters, generatedAt, dataVersion, totalForMetric);
        }
        if (caseMapper.countIndustryTaxonomyTags() == 0) {
            return unavailableIndustryTaxonomy(filters, generatedAt, dataVersion, cases);
        }

        List<CaseItemMapper.AnalyticsIndustryRow> rows = caseMapper.selectEligibleIndustryAnalyticsRows(tagIds);
        rows = rows == null ? List.of() : rows.stream()
                .filter(row -> row != null && row.getTagId() != null && row.getTagId() > 0
                        && row.getLabel() != null && !row.getLabel().isBlank()
                        && row.getValue() != null && row.getValue() > 0)
                .sorted(java.util.Comparator.comparing(CaseItemMapper.AnalyticsIndustryRow::getValue).reversed()
                        .thenComparing(CaseItemMapper.AnalyticsIndustryRow::getTagId))
                .toList();
        long sampleSize = caseMapper.countEligibleIndustryTaggedCases(tagIds);
        long classifiedCount = tagIds.isEmpty()
                ? sampleSize : caseMapper.countEligibleIndustryTaggedCases(List.of());
        long totalEligible = cases;
        long missingCount = Math.max(0L, totalEligible - classifiedCount);
        List<AnalyticsIndustriesVO.Bucket> buckets = rows.stream()
                .map(row -> industryBucket(row, totalEligible))
                .toList();
        List<AnalyticsIndustriesVO.Caveat> caveats = new ArrayList<>();
        caveats.add(new AnalyticsIndustriesVO.Caveat(
                "CANONICAL_CASE_DEDUPLICATION_NOT_READY",
                "案例尚未具备独立 canonical_case_id，当前按已核验案例 ID 去重。",
                "warning", List.of("value", "ratio", "sampleSize")));
        if (buckets.stream().anyMatch(bucket -> bucket.sampleSize() < 3)) {
            caveats.add(new AnalyticsIndustriesVO.Caveat(
                    "LOW_SAMPLE_BUCKETS", "部分行业样本少于 3，仅供核验和下钻，不应作趋势判断。",
                    "warning", List.of("buckets")));
        }
        String status = buckets.isEmpty() ? "empty" : "partial";
        return new AnalyticsIndustriesVO(
                buckets, industryMetric(INDUSTRY_CASE_COUNT, "yellow"), filters,
                sampleSize, missingCount, totalEligible, generatedAt, dataVersion,
                industryFreshness(generatedAt), List.copyOf(caveats), status);
    }

    private List<Long> normalizeIndustryTagIds(List<Long> values) {
        if (values == null || values.isEmpty()) return List.of();
        if (values.size() > 10 || values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(values);
        if (unique.size() != values.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        return unique.stream().sorted().toList();
    }

    private AnalyticsIndustriesVO unavailableIndustryMetric(
            String metricId,
            AnalyticsIndustriesVO.Filters filters,
            OffsetDateTime generatedAt,
            String dataVersion,
            long totalEligible
    ) {
        return new AnalyticsIndustriesVO(
                List.of(), industryMetric(metricId, "red"), filters, 0, totalEligible, totalEligible,
                generatedAt, dataVersion, industryFreshness(generatedAt),
                List.of(new AnalyticsIndustriesVO.Caveat(
                        "ANALYTICS_METRIC_NOT_READY", "该行业指标所需的审核字段尚未达到正式统计条件。",
                        "blocking", List.of("metricId"))), "unavailable");
    }

    private AnalyticsIndustriesVO unavailableIndustryTaxonomy(
            AnalyticsIndustriesVO.Filters filters,
            OffsetDateTime generatedAt,
            String dataVersion,
            long totalEligible
    ) {
        return new AnalyticsIndustriesVO(
                List.of(), industryMetric(INDUSTRY_CASE_COUNT, "red"), filters,
                0, totalEligible, totalEligible, generatedAt, dataVersion,
                industryFreshness(generatedAt),
                List.of(new AnalyticsIndustriesVO.Caveat(
                        "INDUSTRY_TAXONOMY_NOT_READY", "正式行业标签尚未完成审核。",
                        "blocking", List.of("industryTagIds"))), "unavailable");
    }

    private AnalyticsIndustriesVO.Metric industryMetric(String metricId, String readiness) {
        String name = switch (metricId) {
            case "industry.case_share" -> "行业案例占比";
            case "industry.new_case_trend" -> "行业新增案例趋势";
            case "industry.region_distribution" -> "行业地区分布";
            case "industry.related_policy_count" -> "行业相关政策数量";
            default -> "行业案例数量";
        };
        String definition = INDUSTRY_CASE_COUNT.equals(metricId)
                ? "按正式行业标签统计具备已发布、已核验来源链的独立案例 ID 数量。"
                : "该指标定义已冻结，但当前审核字段尚未就绪。";
        return new AnalyticsIndustriesVO.Metric(
                metricId, name, "industry-v1", readiness, definition, "条", true);
    }

    private AnalyticsIndustriesVO.Bucket industryBucket(
            CaseItemMapper.AnalyticsIndustryRow row,
            long totalEligible
    ) {
        BigDecimal ratio = totalEligible == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(row.getValue()).divide(
                        BigDecimal.valueOf(totalEligible), 6, RoundingMode.HALF_UP);
        String bucketId = "industry:" + row.getTagId();
        return new AnalyticsIndustriesVO.Bucket(
                bucketId, row.getTagId(), row.getLabel(), row.getValue(), ratio,
                row.getValue(), 0, row.getValue() >= 3 ? "yellow" : "red",
                new AnalyticsIndustriesVO.Drilldown(
                        "/cases?industryTagId=" + row.getTagId(), List.of("case"), true));
    }

    private AnalyticsIndustriesVO.Freshness industryFreshness(OffsetDateTime generatedAt) {
        LocalDateTime lastUpdated = caseMapper.selectEligibleIndustryAnalyticsLastUpdatedAt();
        if (lastUpdated == null) {
            return new AnalyticsIndustriesVO.Freshness(null, null, -1, "unknown");
        }
        OffsetDateTime watermark = lastUpdated.atZone(ANALYTICS_ZONE).toOffsetDateTime();
        return new AnalyticsIndustriesVO.Freshness(
                watermark, watermark, Math.max(0, ChronoUnit.SECONDS.between(watermark, generatedAt)), "current");
    }

    /** Rebuilds only metrics with a complete server-owned aggregate contract. */
    @Transactional(readOnly = true)
    public AnalyticsSnapshotMaterial rebuildSnapshot(
            String metricId,
            JsonNode filters,
            List<String> selectedBucketIds
    ) {
        if (INDUSTRY_CASE_COUNT.equals(metricId)) {
            return rebuildIndustrySnapshot(filters, selectedBucketIds);
        }
        if (!Set.of("overview.verified_cases", "overview.verified_policies", "overview.verified_sources")
                .contains(metricId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "ANALYTICS_METRIC_NOT_FOUND");
        }
        if (filters == null || !filters.isObject() || filters.size() != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        if (selectedBucketIds == null || !selectedBucketIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        long cases = count("case");
        long policies = count("policy");
        long sources = count("source");
        long value = switch (metricId) {
            case "overview.verified_cases" -> cases;
            case "overview.verified_policies" -> policies;
            default -> sources;
        };
        String dataVersion = version(cases, policies, sources);
        try {
            var payload = objectMapper.createObjectNode();
            payload.put("metricId", metricId);
            payload.put("value", value);
            payload.put("sampleSize", value);
            payload.put("missingCount", 0);
            payload.put("totalEligible", value);
            payload.put("dataVersion", dataVersion);
            payload.put("status", "complete");
            return new AnalyticsSnapshotMaterial(
                    metricId,
                    dataVersion,
                    objectMapper.writeValueAsString(objectMapper.createObjectNode()),
                    objectMapper.writeValueAsString(payload),
                    List.of()
            );
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ANALYTICS_SNAPSHOT_SERIALIZATION_FAILED");
        }
    }

    private AnalyticsSnapshotMaterial rebuildIndustrySnapshot(
            JsonNode filters,
            List<String> selectedBucketIds
    ) {
        JsonNode industryTagIdNode = filters == null ? null : filters.get("industryTagId");
        if (filters == null || !filters.isObject() || filters.size() != 1
                || industryTagIdNode == null || !industryTagIdNode.isIntegralNumber()
                || !industryTagIdNode.canConvertToLong() || industryTagIdNode.asLong() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        long industryTagId = industryTagIdNode.asLong();
        AnalyticsIndustriesVO response = buildIndustries(INDUSTRY_CASE_COUNT, List.of(industryTagId));
        if ("unavailable".equals(response.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "ANALYTICS_METRIC_NOT_READY");
        }
        List<String> requested = selectedBucketIds == null ? List.of() : selectedBucketIds;
        if (!requested.equals(List.of("industry:" + industryTagId))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        List<String> allowed = response.buckets().stream().map(AnalyticsIndustriesVO.Bucket::bucketId).toList();
        if (!allowed.containsAll(requested)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        List<AnalyticsIndustriesVO.Bucket> selected = requested.isEmpty()
                ? response.buckets()
                : response.buckets().stream().filter(bucket -> requested.contains(bucket.bucketId())).toList();
        try {
            var payload = objectMapper.createObjectNode();
            payload.put("metricId", INDUSTRY_CASE_COUNT);
            payload.set("buckets", objectMapper.valueToTree(selected));
            payload.put("sampleSize", response.sampleSize());
            payload.put("missingCount", response.missingCount());
            payload.put("totalEligible", response.totalEligible());
            payload.put("dataVersion", response.dataVersion());
            payload.put("status", response.status());
            return new AnalyticsSnapshotMaterial(
                    INDUSTRY_CASE_COUNT, response.dataVersion(),
                    objectMapper.writeValueAsString(objectMapper.createObjectNode().put("industryTagId", industryTagId)),
                    objectMapper.writeValueAsString(payload), allowed);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ANALYTICS_SNAPSHOT_SERIALIZATION_FAILED");
        }
    }

    private long count(String type) {
        if ("case".equals(type)) return caseMapper.countEligibleAnalyticsRecords();
        if ("policy".equals(type)) return policyMapper.countEligibleAnalyticsRecords();
        return sourceMapper.countEligibleAnalyticsRecords();
    }

    private String version(long cases, long policies, long sources) {
        try {
            String material = cases + "/" + policies + "/" + sources
                    + "\ncases=" + String.join(",", sorted(caseMapper.selectEligibleAnalyticsVersionStamps()))
                    + "\nindustries=" + String.join(",", sorted(caseMapper.selectEligibleIndustryAnalyticsVersionStamps()))
                    + "\npolicies=" + String.join(",", sorted(policyMapper.selectEligibleAnalyticsVersionStamps()))
                    + "\nsources=" + String.join(",", sorted(sourceMapper.selectEligibleAnalyticsVersionStamps()));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return "analytics-v1:" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ANALYTICS_DATA_VERSION_UNAVAILABLE");
        }
    }

    private List<String> sorted(List<String> values) {
        List<String> result = new ArrayList<>(values == null ? List.of() : values);
        result.sort(String::compareTo);
        return result;
    }
}
