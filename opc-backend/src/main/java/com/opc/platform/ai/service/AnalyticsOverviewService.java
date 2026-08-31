package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.vo.AnalyticsIndustriesVO;
import com.opc.platform.ai.vo.AnalyticsOverviewVO;
import com.opc.platform.ai.vo.AnalyticsUnavailableVO;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsOverviewService {
    private static final ZoneId ANALYTICS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String INDUSTRY_CASE_COUNT = "industry.case_count";
    private static final Set<String> INDUSTRY_METRICS = Set.of(
            INDUSTRY_CASE_COUNT, "industry.case_share", "industry.new_case_trend",
            "industry.region_distribution", "industry.related_policy_count");
    private static final Set<String> TECHNOLOGY_METRICS = Set.of(
            "technology.case_count", "technology.adoption_trend",
            "technology.industry_matrix", "technology.related_evidence",
            "technology.completeness");
    private static final Set<String> REVENUE_METRICS = Set.of(
            "revenue.range_distribution", "revenue.median", "revenue.quartiles",
            "revenue.by_industry", "revenue.by_region", "revenue.completeness");
    private static final Set<String> REGION_METRICS = Set.of(
            "region.case_count", "region.policy_count", "region.industry_distribution");
    private static final Set<String> TREND_METRICS = Set.of(
            "trend.policy_publish_time", "trend.case_business_time",
            "technology.adoption_trend", "industry.new_case_trend");
    private static final Set<String> TREND_GRANULARITIES = Set.of("day", "month", "quarter", "year");
    private static final Set<String> REVENUE_PERIODS = Set.of("monthly", "annual", "one_off", "other");
    private static final Set<String> REVENUE_TYPES = Set.of("revenue", "profit", "personal_income", "other");
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
                new AnalyticsOverviewVO.MetricCard(
                        "overview.verified_cases", "已核验案例", null, "条", "Red",
                        "案例尚未具备独立 canonical_case_id，不能发布已核验案例总数。"),
                new AnalyticsOverviewVO.MetricCard(
                        "overview.verified_policies", "已核验政策", policies, "条", "Green", null),
                new AnalyticsOverviewVO.MetricCard(
                        "overview.verified_sources", "已核验来源", sources, "条", "Yellow",
                        "来源尚未具备 canonical_source_id，当前按已核验来源记录 ID 去重。"),
                new AnalyticsOverviewVO.MetricCard(
                        "overview.covered_technologies", "覆盖技术数量", null, "种", "Red",
                        "正式技术 taxonomy 尚未完成审核。")
        ), "partial", materialCounts());
    }

    private List<AnalyticsOverviewVO.MaterialCount> materialCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        List<PolicyMapper.MaterialNatureCountRow> rows = policyMapper.selectMaterialNatureCounts();
        if (rows != null) {
            rows.stream().filter(java.util.Objects::nonNull)
                    .filter(row -> row.getMaterialNature() != null && row.getValue() != null)
                    .forEach(row -> counts.put(row.getMaterialNature(), row.getValue()));
        }
        List<AnalyticsOverviewVO.MaterialCount> result = new ArrayList<>();
        result.add(new AnalyticsOverviewVO.MaterialCount("formal_policy", "正式文件",
                Math.max(0L, counts.getOrDefault("formal_policy", 0L)
                        - countPoliciesByStatusAndNature("expired", "formal_policy"))));
        addMaterialCount(result, counts, "consultation_draft", "征求意见稿");
        addMaterialCount(result, counts, "standard_reference", "标准规范文件");
        addMaterialCount(result, counts, "official_platform_service", "官方平台/服务信息");
        long shown = result.stream().mapToLong(item -> item.value() == null ? 0L : item.value()).sum();
        long total = policyMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
        result.add(new AnalyticsOverviewVO.MaterialCount("other_material", "其他资料",
                Math.max(0L, total - shown)));
        return List.copyOf(result);
    }

    private void addMaterialCount(List<AnalyticsOverviewVO.MaterialCount> result, Map<String, Long> counts,
                                  String code, String label) {
        result.add(new AnalyticsOverviewVO.MaterialCount(code, label, counts.getOrDefault(code, 0L)));
    }

    private long countPoliciesByStatusAndNature(String status, String nature) {
        return policyMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.opc.platform.policy.entity.Policy>()
                .eq("status", status).eq("material_nature", nature));
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

    @Transactional(readOnly = true)
    public AnalyticsUnavailableVO technologies(
            AuthenticatedUser user,
            String requestedMetricId,
            List<Long> technologyTagIds
    ) {
        requireUser(user);
        String metricId = normalizedMetricId(requestedMetricId, "technology.case_count", TECHNOLOGY_METRICS);
        List<Long> normalizedTagIds = normalizeUnavailableTagIds(technologyTagIds);
        if (!normalizedTagIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "ANALYTICS_TECHNOLOGY_FILTER_UNAVAILABLE");
        }
        long cases = count("case");
        long policies = count("policy");
        long sources = count("source");
        OffsetDateTime generatedAt = OffsetDateTime.now(ANALYTICS_ZONE);
        return new AnalyticsUnavailableVO(
                List.of(),
                new AnalyticsUnavailableVO.Metric(
                        metricId, technologyMetricName(metricId), "phase3-metrics-v1", "Red",
                        "正式技术 taxonomy 与已审核关系尚未建立；不得从 ai_tools 或自由标签推断。",
                        "条", true),
                Map.of("technologyTagIds", normalizedTagIds),
                0, cases, cases, generatedAt, version(cases, policies, sources),
                new AnalyticsUnavailableVO.Freshness(null, null, -1, "unknown"),
                List.of(new AnalyticsUnavailableVO.Caveat(
                        "TECHNOLOGY_TAXONOMY_NOT_READY",
                        "正式技术 taxonomy 尚未完成审核。",
                        "blocking", List.of("technologyTagIds"))),
                null, "unavailable");
    }

    @Transactional(readOnly = true)
    public AnalyticsUnavailableVO revenue(
            AuthenticatedUser user,
            String requestedMetricId,
            String requestedCurrency,
            String requestedPeriod,
            String requestedType
    ) {
        requireUser(user);
        String metricId = normalizedMetricId(
                requestedMetricId, "revenue.range_distribution", REVENUE_METRICS);
        if (requestedCurrency == null || requestedCurrency.isBlank()
                || requestedPeriod == null || requestedPeriod.isBlank()
                || requestedType == null || requestedType.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "REVENUE_COMPARABILITY_REQUIRED");
        }
        String currency = requestedCurrency.trim().toUpperCase(java.util.Locale.ROOT);
        String period = requestedPeriod.trim();
        String type = requestedType.trim();
        if (!currency.matches("[A-Z]{3}") || !REVENUE_PERIODS.contains(period)
                || !REVENUE_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        long cases = count("case");
        long policies = count("policy");
        long sources = count("source");
        OffsetDateTime generatedAt = OffsetDateTime.now(ANALYTICS_ZONE);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("currency", currency);
        filters.put("revenuePeriod", period);
        filters.put("revenueType", type);
        return new AnalyticsUnavailableVO(
                List.of(),
                new AnalyticsUnavailableVO.Metric(
                        metricId, revenueMetricName(metricId), "phase3-metrics-v1", "Red",
                        "规范化收入金额、币种、周期、类型、状态、时点和来源字段尚未建立。",
                        currency, false),
                Map.copyOf(filters),
                0, cases, cases, generatedAt, version(cases, policies, sources),
                new AnalyticsUnavailableVO.Freshness(null, null, -1, "unknown"),
                List.of(new AnalyticsUnavailableVO.Caveat(
                        "REVENUE_SCHEMA_NOT_READY",
                        "收入字段尚未结构化，不能从业务模式、结果或正文数字推断。",
                        "blocking", List.of("data"))),
                null, "unavailable");
    }

    @Transactional(readOnly = true)
    public AnalyticsUnavailableVO regions(
            AuthenticatedUser user,
            String requestedMetricId,
            String requestedRegionRole
    ) {
        requireUser(user);
        String metricId = normalizedMetricId(
                requestedMetricId, "region.case_count", REGION_METRICS);
        String regionRole = requestedRegionRole == null || requestedRegionRole.isBlank()
                ? ("region.policy_count".equals(metricId) ? "policy_applicability" : "operation")
                : requestedRegionRole.trim();
        if ("region.policy_count".equals(metricId)) {
            if (!"policy_applicability".equals(regionRole)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_REGION_ROLE_INVALID");
            }
            return policyRegions(regionRole);
        }
        if (!"region.case_count".equals(metricId)
                || !("operation".equals(regionRole) || "registration".equals(regionRole))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        return caseRegions(regionRole);
    }

    private AnalyticsUnavailableVO policyRegions(String regionRole) {
        List<PolicyMapper.AnalyticsProvinceRow> groupedRows =
                policyMapper.selectEligibleProvinceAnalyticsRows();
        groupedRows = groupedRows == null ? List.of() : groupedRows.stream()
                .filter(java.util.Objects::nonNull)
                .filter(row -> row.getRegionId() != null && row.getRegionId() > 0)
                .filter(row -> row.getLabel() != null && !row.getLabel().isBlank())
                .filter(row -> row.getValue() != null && row.getValue() > 0)
                .toList();
        long totalEligible = count("policy");
        List<AnalyticsUnavailableVO.RegionRow> values = groupedRows.stream()
                .map(row -> provinceRegionRow(
                        row.getRegionId(), row.getLabel(), row.getValue(), regionRole,
                        totalEligible, "Green", true))
                .sorted(java.util.Comparator.comparingLong(AnalyticsUnavailableVO.RegionRow::value)
                        .reversed().thenComparing(AnalyticsUnavailableVO.RegionRow::regionId))
                .toList();
        List<Object> rows = new ArrayList<>(values);
        long covered = values.stream().mapToLong(AnalyticsUnavailableVO.RegionRow::value).sum();
        long missing = Math.max(0L, totalEligible - covered);
        OffsetDateTime generatedAt = OffsetDateTime.now(ANALYTICS_ZONE);
        AnalyticsUnavailableVO.Freshness freshness = policyFreshness(generatedAt);
        List<AnalyticsUnavailableVO.Caveat> caveats = missing == 0 ? List.of() : List.of(
                new AnalyticsUnavailableVO.Caveat(
                        "POLICY_REGION_MISSING",
                        "全国性政策及缺少可解析省份的合格政策不纳入省级排名，已计入未归属数量。",
                        "warning", List.of("rows", "coverage")));
        String status = rows.isEmpty() ? "empty" : missing > 0 ? "partial" : "complete";
        long cases = count("case");
        long sources = count("source");
        return new AnalyticsUnavailableVO(
                rows,
                new AnalyticsUnavailableVO.Metric(
                        "region.policy_count", "各省政策数量", "province-ranking-v1", "Green",
                        "将省、地市和区县适用地区统一上卷至所属省，统计具备完整已核验来源链的独立政策 ID 数量。",
                        "条", false),
                Map.of("regionRole", regionRole, "regionLevel", "province"),
                covered, missing, totalEligible, generatedAt,
                version(cases, totalEligible, sources), freshness, caveats,
                new AnalyticsUnavailableVO.Drilldown(
                        "/api/analytics/drilldown", List.of("policy"), null, true),
                status, true, null, freshness, true,
                AnalyticsUnavailableVO.Coverage.from(totalEligible, covered, missing),
                rows, List.of(), null, false, covered);
    }

    private AnalyticsUnavailableVO caseRegions(String regionRole) {
        List<CaseItemMapper.AnalyticsProvinceRow> groupedRows =
                caseMapper.selectEligibleProvinceAnalyticsRows();
        groupedRows = groupedRows == null ? List.of() : groupedRows.stream()
                .filter(java.util.Objects::nonNull)
                .filter(row -> row.getRegionId() != null && row.getRegionId() > 0)
                .filter(row -> row.getLabel() != null && !row.getLabel().isBlank())
                .filter(row -> row.getValue() != null && row.getValue() > 0)
                .toList();
        long totalEligible = count("case");
        List<AnalyticsUnavailableVO.RegionRow> values = groupedRows.stream()
                .map(row -> provinceRegionRow(
                        row.getRegionId(), row.getLabel(), row.getValue(), regionRole,
                        totalEligible, "Yellow", false))
                .sorted(java.util.Comparator.comparingLong(AnalyticsUnavailableVO.RegionRow::value)
                        .reversed().thenComparing(AnalyticsUnavailableVO.RegionRow::regionId))
                .toList();
        List<Object> rows = new ArrayList<>(values);
        long covered = values.stream().mapToLong(AnalyticsUnavailableVO.RegionRow::value).sum();
        long missing = Math.max(0L, totalEligible - covered);
        OffsetDateTime generatedAt = OffsetDateTime.now(ANALYTICS_ZONE);
        AnalyticsUnavailableVO.Freshness freshness = caseFreshness(generatedAt);
        List<AnalyticsUnavailableVO.Caveat> caveats = new ArrayList<>();
        caveats.add(new AnalyticsUnavailableVO.Caveat(
                "CANONICAL_CASE_DEDUPLICATION_NOT_READY",
                "当前按已核验案例记录 ID 去重；与全部收录案例总量分开解释。",
                "warning", List.of("rows", "sampleSize")));
        if (missing > 0) {
            caveats.add(new AnalyticsUnavailableVO.Caveat(
                    "CASE_PROVINCE_MISSING",
                    "缺少可解析省份的合格案例不纳入省级排名，已计入未归属数量。",
                    "warning", List.of("rows", "coverage")));
        }
        String status = rows.isEmpty() ? "empty" : missing > 0 ? "partial" : "complete";
        long policies = count("policy");
        long sources = count("source");
        return new AnalyticsUnavailableVO(
                rows,
                new AnalyticsUnavailableVO.Metric(
                        "region.case_count", "各省案例数量", "province-ranking-v1", "Yellow",
                        "将已核验案例当前地区上卷至所属省，按案例记录 ID 统计数量。",
                        "条", false),
                Map.of("regionRole", regionRole, "regionLevel", "province"),
                covered, missing, totalEligible, generatedAt,
                version(totalEligible, policies, sources), freshness, List.copyOf(caveats),
                null, status, true, null, freshness, true,
                AnalyticsUnavailableVO.Coverage.from(totalEligible, covered, missing),
                rows, List.of(), null, false, covered);
    }

    private AnalyticsUnavailableVO.RegionRow provinceRegionRow(
            long regionId,
            String label,
            long value,
            String regionRole,
            long totalEligible,
            String readiness,
            boolean drilldownAvailable
    ) {
        BigDecimal ratio = totalEligible == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(value).divide(
                        BigDecimal.valueOf(totalEligible), 6, RoundingMode.HALF_UP);
        return new AnalyticsUnavailableVO.RegionRow(
                "region:" + regionId, regionId, null,
                "province", regionRole, label, value, ratio, value,
                readiness, drilldownAvailable ? new AnalyticsUnavailableVO.Drilldown(
                        "/api/analytics/drilldown", List.of("policy"), null, true) : null);
    }

    @Transactional(readOnly = true)
    public AnalyticsUnavailableVO trends(
            AuthenticatedUser user,
            String requestedMetricId,
            String requestedDateFrom,
            String requestedDateTo,
            String requestedGranularity,
            Long requestedRegionId,
            String requestedRegionRole,
            List<Long> requestedIndustryTagIds,
            List<Long> requestedTechnologyTagIds
    ) {
        requireUser(user);
        String metricId = normalizedMetricId(
                requestedMetricId, "trend.policy_publish_time", TREND_METRICS);
        LocalDate dateFrom = parseTrendDate(requestedDateFrom);
        LocalDate dateTo = parseTrendDate(requestedDateTo);
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        if (dateTo != null && dateTo.isAfter(LocalDate.now(ANALYTICS_ZONE))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        String granularity = requestedGranularity == null || requestedGranularity.isBlank()
                ? null : requestedGranularity.trim();
        if (granularity != null && !TREND_GRANULARITIES.contains(granularity)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        List<Long> industryTagIds = normalizeIndustryTagIds(requestedIndustryTagIds);
        List<Long> technologyTagIds = normalizeUnavailableTagIds(requestedTechnologyTagIds);
        if (!technologyTagIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "ANALYTICS_TECHNOLOGY_FILTER_UNAVAILABLE");
        }
        String regionRole = requestedRegionRole == null || requestedRegionRole.isBlank()
                ? defaultTrendRegionRole(metricId) : requestedRegionRole.trim();
        if (!Set.of("operation", "registration", "legacy_related_region", "policy_applicability")
                .contains(regionRole)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        if (requestedRegionId != null && requestedRegionId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }

        if ("trend.policy_publish_time".equals(metricId)) {
            if (!"policy_applicability".equals(regionRole) || !industryTagIds.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
            }
            return policyPublishTrend(
                    dateFrom, dateTo, granularity, requestedRegionId, regionRole,
                    industryTagIds, technologyTagIds);
        }

        long cases = count("case");
        long policies = count("policy");
        long sources = count("source");
        long totalEligible = metricId.startsWith("trend.policy") ? policies : cases;
        Map<String, Object> filters = new LinkedHashMap<>();
        if (dateFrom != null) filters.put("dateFrom", dateFrom.toString());
        if (dateTo != null) filters.put("dateTo", dateTo.toString());
        if (granularity != null) filters.put("granularity", granularity);
        if (requestedRegionId != null) filters.put("regionId", requestedRegionId);
        filters.put("regionRole", regionRole);
        if (!industryTagIds.isEmpty()) filters.put("industryTagIds", industryTagIds);
        filters.put("technologyTagIds", technologyTagIds);
        String caveatCode = trendCaveatCode(metricId);
        return new AnalyticsUnavailableVO(
                List.of(),
                new AnalyticsUnavailableVO.Metric(
                        metricId, trendMetricName(metricId), "phase3-metrics-v1", "Red",
                        trendDefinition(metricId), "条", false),
                Map.copyOf(filters),
                0, totalEligible, totalEligible, OffsetDateTime.now(ANALYTICS_ZONE),
                version(cases, policies, sources),
                new AnalyticsUnavailableVO.Freshness(null, null, -1, "unknown"),
                List.of(new AnalyticsUnavailableVO.Caveat(
                        caveatCode, trendCaveatMessage(caveatCode), "blocking", List.of("data"))),
                null, "unavailable");
    }

    private AnalyticsUnavailableVO policyPublishTrend(
            LocalDate requestedDateFrom,
            LocalDate requestedDateTo,
            String requestedGranularity,
            Long regionId,
            String regionRole,
            List<Long> industryTagIds,
            List<Long> technologyTagIds
    ) {
        LocalDate dateTo = requestedDateTo == null ? LocalDate.now(ANALYTICS_ZONE) : requestedDateTo;
        LocalDate dateFrom = requestedDateFrom == null
                ? dateTo.minusMonths(35).withDayOfMonth(1) : requestedDateFrom;
        String granularity = requestedGranularity == null ? "month" : requestedGranularity;
        if (dateFrom.isAfter(dateTo)
                || dateFrom.isBefore(dateTo.minusYears(10))
                || ("day".equals(granularity) && dateFrom.isBefore(dateTo.minusDays(89)))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }

        List<PolicyMapper.AnalyticsPolicyRow> eligibleRows = policyMapper.selectEligibleAnalyticsRows(regionId);
        eligibleRows = eligibleRows == null ? List.of() : eligibleRows.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        long covered = eligibleRows.stream().filter(row -> row.getPublishDate() != null).count();
        long missing = Math.max(0L, eligibleRows.size() - covered);
        List<AnalyticsUnavailableVO.TrendPoint> points = policyTrendPoints(
                eligibleRows, dateFrom, dateTo, granularity);
        long sampleSize = points.stream().mapToLong(AnalyticsUnavailableVO.TrendPoint::value).sum();
        List<Object> series = new ArrayList<>(points);
        OffsetDateTime generatedAt = OffsetDateTime.now(ANALYTICS_ZONE);
        AnalyticsUnavailableVO.Freshness freshness = policyFreshness(generatedAt);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("dateFrom", dateFrom.toString());
        filters.put("dateTo", dateTo.toString());
        filters.put("granularity", granularity);
        if (regionId != null) filters.put("regionId", regionId);
        filters.put("regionRole", regionRole);
        filters.put("industryTagIds", industryTagIds);
        filters.put("technologyTagIds", technologyTagIds);
        List<AnalyticsUnavailableVO.Caveat> caveats = missing == 0 ? List.of() : List.of(
                new AnalyticsUnavailableVO.Caveat(
                        "POLICY_PUBLISH_DATE_MISSING",
                        "部分合格政策缺少 publish_date，已从趋势分子中排除并计入覆盖缺失。",
                        "warning", List.of("series", "coverage")));
        String status = sampleSize == 0 ? "empty" : missing > 0 ? "partial" : "complete";
        long cases = count("case");
        long policies = count("policy");
        long sources = count("source");
        return new AnalyticsUnavailableVO(
                series,
                new AnalyticsUnavailableVO.Metric(
                        "trend.policy_publish_time", "政策发布时间趋势", "phase3-metrics-v1", "Green",
                        "按 publish_date 分桶统计具备完整已核验来源链的独立政策 ID 数量。",
                        "条", false),
                Map.copyOf(filters), sampleSize, missing, eligibleRows.size(), generatedAt,
                version(cases, policies, sources), freshness, caveats,
                new AnalyticsUnavailableVO.Drilldown(
                        "/api/analytics/drilldown", List.of("policy"), null, false),
                status, true, null, freshness, true,
                AnalyticsUnavailableVO.Coverage.from(eligibleRows.size(), covered, missing),
                List.of(), series, null, false, sampleSize);
    }

    private List<AnalyticsUnavailableVO.TrendPoint> policyTrendPoints(
            List<PolicyMapper.AnalyticsPolicyRow> rows,
            LocalDate dateFrom,
            LocalDate dateTo,
            String granularity
    ) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        rows.stream()
                .map(PolicyMapper.AnalyticsPolicyRow::getPublishDate)
                .filter(java.util.Objects::nonNull)
                .filter(date -> !date.isBefore(dateFrom) && !date.isAfter(dateTo))
                .map(date -> periodStart(date, granularity))
                .forEach(period -> counts.merge(period, 1L, Long::sum));
        List<AnalyticsUnavailableVO.TrendPoint> result = new ArrayList<>();
        LocalDate cursor = periodStart(dateFrom, granularity);
        LocalDate endExclusive = nextPeriod(periodStart(dateTo, granularity), granularity);
        while (cursor.isBefore(endExclusive)) {
            LocalDate next = nextPeriod(cursor, granularity);
            long value = counts.getOrDefault(cursor, 0L);
            result.add(new AnalyticsUnavailableVO.TrendPoint(
                    periodBucketId(cursor, granularity), cursor, next,
                    value, value, 0, value == 0));
            cursor = next;
        }
        return List.copyOf(result);
    }

    private LocalDate periodStart(LocalDate date, String granularity) {
        return switch (granularity) {
            case "day" -> date;
            case "quarter" -> LocalDate.of(
                    date.getYear(), ((date.getMonthValue() - 1) / 3) * 3 + 1, 1);
            case "year" -> LocalDate.of(date.getYear(), 1, 1);
            default -> date.withDayOfMonth(1);
        };
    }

    private LocalDate nextPeriod(LocalDate date, String granularity) {
        return switch (granularity) {
            case "day" -> date.plusDays(1);
            case "quarter" -> date.plusMonths(3);
            case "year" -> date.plusYears(1);
            default -> date.plusMonths(1);
        };
    }

    private String periodBucketId(LocalDate date, String granularity) {
        return switch (granularity) {
            case "day" -> date.toString();
            case "quarter" -> date.getYear() + "-Q" + (((date.getMonthValue() - 1) / 3) + 1);
            case "year" -> Integer.toString(date.getYear());
            default -> YearMonth.from(date).toString();
        };
    }

    private AnalyticsUnavailableVO.Freshness policyFreshness(OffsetDateTime generatedAt) {
        LocalDateTime lastUpdated = policyMapper.selectEligibleAnalyticsLastUpdatedAt();
        if (lastUpdated == null) {
            return new AnalyticsUnavailableVO.Freshness(null, null, -1, "unknown");
        }
        OffsetDateTime watermark = lastUpdated.atZone(ANALYTICS_ZONE).toOffsetDateTime();
        return new AnalyticsUnavailableVO.Freshness(
                watermark, watermark,
                Math.max(0L, ChronoUnit.SECONDS.between(watermark, generatedAt)), "current");
    }

    private AnalyticsUnavailableVO.Freshness caseFreshness(OffsetDateTime generatedAt) {
        LocalDateTime lastUpdated = caseMapper.selectEligibleAnalyticsLastUpdatedAt();
        if (lastUpdated == null) {
            return new AnalyticsUnavailableVO.Freshness(null, null, -1, "unknown");
        }
        OffsetDateTime watermark = lastUpdated.atZone(ANALYTICS_ZONE).toOffsetDateTime();
        return new AnalyticsUnavailableVO.Freshness(
                watermark, watermark,
                Math.max(0L, ChronoUnit.SECONDS.between(watermark, generatedAt)), "current");
    }

    @Transactional(readOnly = true)
    public AnalyticsUnavailableVO drilldown(
            AuthenticatedUser user,
            String requestedMetricId,
            String requestedDataVersion,
            String requestedEntityType,
            String requestedBucketId,
            String requestedCursor,
            int requestedLimit
    ) {
        requireUser(user);
        String metricId = requestedMetricId == null ? "" : requestedMetricId.trim();
        if (!isKnownAnalyticsMetric(metricId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "ANALYTICS_METRIC_NOT_FOUND");
        }
        String dataVersion = requestedDataVersion == null ? "" : requestedDataVersion.trim();
        String entityType = requestedEntityType == null ? "" : requestedEntityType.trim();
        String bucketId = requestedBucketId == null || requestedBucketId.isBlank()
                ? null : requestedBucketId.trim();
        String cursor = requestedCursor == null || requestedCursor.isBlank()
                ? null : requestedCursor.trim();
        if (dataVersion.isEmpty() || dataVersion.length() > 128
                || !Set.of("case", "policy", "source").contains(entityType)
                || (bucketId != null && bucketId.length() > 128)
                || (cursor != null && cursor.length() > 1024)
                || requestedLimit < 1 || requestedLimit > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }

        long cases = count("case");
        long policies = count("policy");
        long sources = count("source");
        if ("region.policy_count".equals(metricId) && "policy".equals(entityType)) {
            String currentDataVersion = version(cases, policies, sources);
            if (!currentDataVersion.equals(dataVersion)) {
                throw new BusinessException(ErrorCode.CONFLICT, "ANALYTICS_DATA_VERSION_STALE");
            }
            return policyRegionDrilldown(
                    metricId, currentDataVersion, bucketId, cursor, requestedLimit);
        }
        long totalEligible = switch (entityType) {
            case "case" -> cases;
            case "policy" -> policies;
            default -> sources;
        };
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("entityType", entityType);
        if (bucketId != null) filters.put("bucketId", bucketId);
        filters.put("limit", requestedLimit);
        return new AnalyticsUnavailableVO(
                List.of(),
                new AnalyticsUnavailableVO.Metric(
                        metricId, "统计下钻", "phase3-metrics-v1", "Red",
                        "下钻必须使用与聚合完全一致的合格 ID 集，并绑定数据版本和筛选条件。",
                        "", false),
                Map.copyOf(filters),
                0, totalEligible, totalEligible, OffsetDateTime.now(ANALYTICS_ZONE),
                dataVersion,
                new AnalyticsUnavailableVO.Freshness(null, null, -1, "unknown"),
                List.of(new AnalyticsUnavailableVO.Caveat(
                        "ANALYTICS_DRILLDOWN_NOT_READY",
                        "版本绑定的合格 ID 下钻查询尚未实现，不能回退到仅按发布状态筛选的工作区列表。",
                        "blocking", List.of("data", "drilldown"))),
                new AnalyticsUnavailableVO.Drilldown(
                        "/api/analytics/drilldown", List.of(entityType), null, false),
                "unavailable");
    }

    private AnalyticsUnavailableVO policyRegionDrilldown(
            String metricId,
            String dataVersion,
            String bucketId,
            String cursor,
            int limit
    ) {
        if (bucketId == null || !bucketId.matches("region:[1-9]\\d*")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        long regionId;
        try {
            regionId = Long.parseLong(bucketId.substring("region:".length()));
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        int offset = decodeDrilldownOffset(cursor, metricId, dataVersion, bucketId);
        List<PolicyMapper.AnalyticsPolicyRow> eligibleRows = policyMapper.selectEligibleAnalyticsRows(null);
        eligibleRows = eligibleRows == null ? List.of() : eligibleRows.stream()
                .filter(java.util.Objects::nonNull)
                .filter(row -> Long.valueOf(regionId).equals(row.getProvinceId()))
                .toList();
        if (offset > eligibleRows.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
        int pageEnd = Math.min(eligibleRows.size(), offset + limit);
        List<AnalyticsUnavailableVO.PolicyDrilldownRow> page = eligibleRows.subList(offset, pageEnd).stream()
                .map(this::policyDrilldownRow)
                .toList();
        List<Object> rows = new ArrayList<>(page);
        boolean hasMore = pageEnd < eligibleRows.size();
        String nextCursor = hasMore
                ? encodeDrilldownCursor(pageEnd, metricId, dataVersion, bucketId) : null;
        OffsetDateTime generatedAt = OffsetDateTime.now(ANALYTICS_ZONE);
        AnalyticsUnavailableVO.Freshness freshness = policyFreshness(generatedAt);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("entityType", "policy");
        filters.put("bucketId", bucketId);
        filters.put("regionId", regionId);
        filters.put("regionRole", "policy_applicability");
        filters.put("limit", limit);
        long aggregateValue = eligibleRows.size();
        return new AnalyticsUnavailableVO(
                rows,
                new AnalyticsUnavailableVO.Metric(
                        metricId, "地区政策下钻", "phase3-metrics-v1", "Green",
                        "返回与地区政策聚合相同合格集合中的已核验政策。", "条", false),
                Map.copyOf(filters), aggregateValue, 0, aggregateValue, generatedAt,
                dataVersion, freshness, List.of(),
                new AnalyticsUnavailableVO.Drilldown(
                        "/api/analytics/drilldown", List.of("policy"), nextCursor, true),
                rows.isEmpty() ? "empty" : "complete", true, null, freshness, true,
                AnalyticsUnavailableVO.Coverage.from(aggregateValue, aggregateValue, 0),
                rows, List.of(), nextCursor, hasMore, aggregateValue);
    }

    private AnalyticsUnavailableVO.PolicyDrilldownRow policyDrilldownRow(
            PolicyMapper.AnalyticsPolicyRow row
    ) {
        return new AnalyticsUnavailableVO.PolicyDrilldownRow(
                row.getId(), row.getTitle(), row.getPublishDate(), row.getRegionId(),
                row.getRegionName(), row.getIssuingBody(), row.getPolicyType(),
                row.getAiEvidenceStatus(), "/policies/" + row.getId());
    }

    private int decodeDrilldownOffset(
            String cursor,
            String metricId,
            String dataVersion,
            String bucketId
    ) {
        if (cursor == null) return 0;
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 4
                    || !metricId.equals(parts[1])
                    || !dataVersion.equals(parts[2])
                    || !bucketId.equals(parts[3])) {
                throw new IllegalArgumentException("cursor binding mismatch");
            }
            int offset = Integer.parseInt(parts[0]);
            if (offset < 0) throw new IllegalArgumentException("negative cursor offset");
            return offset;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "ANALYTICS_CURSOR_STALE");
        }
    }

    private String encodeDrilldownCursor(
            int offset,
            String metricId,
            String dataVersion,
            String bucketId
    ) {
        String material = offset + "|" + metricId + "|" + dataVersion + "|" + bucketId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(material.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isKnownAnalyticsMetric(String metricId) {
        return Set.of(
                        "overview.verified_cases", "overview.verified_policies",
                        "overview.verified_sources", "overview.covered_regions",
                        "overview.covered_industries", "overview.covered_technologies",
                        "overview.data_completeness")
                .contains(metricId)
                || INDUSTRY_METRICS.contains(metricId)
                || TECHNOLOGY_METRICS.contains(metricId)
                || REVENUE_METRICS.contains(metricId)
                || REGION_METRICS.contains(metricId)
                || TREND_METRICS.contains(metricId);
    }

    private LocalDate parseTrendDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_INVALID_FILTER");
        }
    }

    private String defaultTrendRegionRole(String metricId) {
        return metricId.startsWith("trend.policy") ? "policy_applicability" : "operation";
    }

    private String trendCaveatCode(String metricId) {
        return switch (metricId) {
            case "trend.case_business_time", "industry.new_case_trend" -> "CASE_BUSINESS_TIME_NOT_READY";
            case "technology.adoption_trend" -> "TECHNOLOGY_TAXONOMY_NOT_READY";
            default -> "TREND_AGGREGATION_NOT_READY";
        };
    }

    private String trendCaveatMessage(String caveatCode) {
        return switch (caveatCode) {
            case "CASE_BUSINESS_TIME_NOT_READY" -> "案例业务发布时间或发生时间尚未结构化，不能使用 created_at 或 accessed_at 回退。";
            case "TECHNOLOGY_TAXONOMY_NOT_READY" -> "正式技术 taxonomy 与业务时间关系尚未完成审核。";
            default -> "趋势聚合查询尚未完成，当前不返回推测序列。";
        };
    }

    private String trendMetricName(String metricId) {
        return switch (metricId) {
            case "trend.case_business_time" -> "案例业务时间趋势";
            case "technology.adoption_trend" -> "技术采用趋势";
            case "industry.new_case_trend" -> "行业新增案例趋势";
            default -> "政策发布时间趋势";
        };
    }

    private String trendDefinition(String metricId) {
        return switch (metricId) {
            case "trend.case_business_time", "industry.new_case_trend" ->
                    "按明确的案例业务发布时间或发生时间统计独立案例，缺失时不回退采集时间。";
            case "technology.adoption_trend" -> "按已审核技术关系和案例业务时间统计技术采用序列。";
            default -> "按合格政策 publish_date 分桶统计政策数量。";
        };
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
                buckets, industryMetric(INDUSTRY_CASE_COUNT, "Yellow"), filters,
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

    private List<Long> normalizeUnavailableTagIds(List<Long> values) {
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

    private String normalizedMetricId(String requested, String fallback, Set<String> allowed) {
        String metricId = requested == null || requested.isBlank() ? fallback : requested.trim();
        if (!allowed.contains(metricId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "ANALYTICS_METRIC_NOT_FOUND");
        }
        return metricId;
    }

    private String technologyMetricName(String metricId) {
        return switch (metricId) {
            case "technology.adoption_trend" -> "技术采用趋势";
            case "technology.industry_matrix" -> "技术与行业组合";
            case "technology.related_evidence" -> "技术相关案例和政策";
            case "technology.completeness" -> "技术数据完整度";
            default -> "技术标签案例数量";
        };
    }

    private String revenueMetricName(String metricId) {
        return switch (metricId) {
            case "revenue.median" -> "收入中位数";
            case "revenue.quartiles" -> "收入四分位数";
            case "revenue.by_industry" -> "分行业收入分布";
            case "revenue.by_region" -> "分地区收入分布";
            case "revenue.completeness" -> "收入数据覆盖率";
            default -> "收入区间分布";
        };
    }

    private void requireUser(AuthenticatedUser user) {
        if (user == null || user.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
    }

    private AnalyticsIndustriesVO unavailableIndustryMetric(
            String metricId,
            AnalyticsIndustriesVO.Filters filters,
            OffsetDateTime generatedAt,
            String dataVersion,
            long totalEligible
    ) {
        return new AnalyticsIndustriesVO(
                List.of(), industryMetric(metricId, "Red"), filters, 0, totalEligible, totalEligible,
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
                List.of(), industryMetric(INDUSTRY_CASE_COUNT, "Red"), filters,
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
                row.getValue(), 0, row.getValue() >= 3 ? "Yellow" : "Red",
                new AnalyticsIndustriesVO.Drilldown(
                        "/api/analytics/drilldown", List.of("case"), false));
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
