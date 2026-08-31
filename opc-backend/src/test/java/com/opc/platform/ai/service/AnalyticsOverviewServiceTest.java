package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.vo.AnalyticsUnavailableVO;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsOverviewServiceTest {

    private final AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");

    @Test
    void returnsVerifiedPolicyPublishTrendWithExplicitEmptyBucketsAndCoverage() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        stubAnalyticsVersion(cases, policies, sources, 0L, 3L, 3L);
        when(policies.selectEligibleAnalyticsRows(null)).thenReturn(List.of(
                policyRow(11L, LocalDate.of(2026, 1, 5), 7L, "浙江省"),
                policyRow(12L, LocalDate.of(2026, 1, 20), 7L, "浙江省"),
                policyRow(13L, null, 9L, "江苏省")
        ));
        when(policies.selectEligibleAnalyticsLastUpdatedAt())
                .thenReturn(LocalDateTime.of(2026, 3, 31, 9, 30));
        AnalyticsOverviewService service = new AnalyticsOverviewService(
                cases, policies, sources, new ObjectMapper());

        var response = service.trends(
                owner, "trend.policy_publish_time", "2026-01-01", "2026-03-31",
                "month", null, null, List.of(), List.of());

        assertEquals(true, response.available());
        assertEquals(null, response.unavailableReason());
        assertEquals(true, response.verifiedOnly());
        assertEquals(3L, response.coverage().eligible());
        assertEquals(2L, response.coverage().covered());
        assertEquals(1L, response.coverage().missing());
        assertEquals("current", response.dataFreshness().status());
        assertEquals("partial", response.status());
        assertEquals(3, response.series().size());
        var january = (AnalyticsUnavailableVO.TrendPoint) response.series().get(0);
        var february = (AnalyticsUnavailableVO.TrendPoint) response.series().get(1);
        assertEquals("2026-01", january.bucketId());
        assertEquals(2L, january.value());
        assertEquals("2026-02", february.bucketId());
        assertEquals(0L, february.value());
        assertEquals(true, february.syntheticEmptyBucket());
    }

    @Test
    void returnsVerifiedPolicyApplicabilityRegionRows() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        stubAnalyticsVersion(cases, policies, sources, 0L, 3L, 3L);
        when(policies.selectEligibleProvinceAnalyticsRows()).thenReturn(List.of(
                new PolicyMapper.AnalyticsProvinceRow(33L, "浙江省", 2L),
                new PolicyMapper.AnalyticsProvinceRow(32L, "江苏省", 1L)
        ));
        AnalyticsOverviewService service = new AnalyticsOverviewService(
                cases, policies, sources, new ObjectMapper());

        var response = service.regions(owner, "region.policy_count", "policy_applicability");

        assertEquals(true, response.available());
        assertEquals("complete", response.status());
        assertEquals(3L, response.coverage().covered());
        assertEquals(2, response.rows().size());
        var first = (AnalyticsUnavailableVO.RegionRow) response.rows().get(0);
        assertEquals("region:33", first.bucketId());
        assertEquals("province", first.level());
        assertEquals("policy_applicability", first.regionRole());
        assertEquals(2L, first.value());
        assertEquals("Green", first.readiness());
    }

    @Test
    void returnsVerifiedCaseProvinceRankingWithExplicitRecordIdCaveat() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        stubAnalyticsVersion(cases, policies, sources, 5L, 0L, 5L);
        when(cases.selectEligibleProvinceAnalyticsRows()).thenReturn(List.of(
                new CaseItemMapper.AnalyticsProvinceRow(44L, "广东省", 3L),
                new CaseItemMapper.AnalyticsProvinceRow(33L, "浙江省", 1L)
        ));
        AnalyticsOverviewService service = new AnalyticsOverviewService(
                cases, policies, sources, new ObjectMapper());

        var response = service.regions(owner, "region.case_count", "operation");

        assertEquals(true, response.available());
        assertEquals("partial", response.status());
        assertEquals(5L, response.coverage().eligible());
        assertEquals(4L, response.coverage().covered());
        assertEquals(1L, response.coverage().missing());
        assertEquals(2, response.rows().size());
        var first = (AnalyticsUnavailableVO.RegionRow) response.rows().get(0);
        assertEquals("region:44", first.bucketId());
        assertEquals("Yellow", first.readiness());
        assertEquals("CANONICAL_CASE_DEDUPLICATION_NOT_READY", response.caveats().get(0).code());
    }

    @Test
    void returnsOnlyTheVersionBoundEligiblePoliciesForARegionDrilldown() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        stubAnalyticsVersion(cases, policies, sources, 0L, 3L, 3L);
        when(policies.selectEligibleAnalyticsRows(null)).thenReturn(List.of(
                policyRow(11L, LocalDate.of(2026, 1, 5), 7L, "浙江省"),
                policyRow(12L, LocalDate.of(2026, 1, 20), 7L, "浙江省"),
                policyRow(13L, LocalDate.of(2026, 2, 1), 9L, "江苏省")
        ));
        AnalyticsOverviewService service = new AnalyticsOverviewService(
                cases, policies, sources, new ObjectMapper());
        String dataVersion = service.overview(owner).dataVersion();

        var response = service.drilldown(
                owner, "region.policy_count", dataVersion, "policy", "region:7", null, 50);

        assertEquals(true, response.available());
        assertEquals(dataVersion, response.dataVersion());
        assertEquals(2L, response.aggregateValue());
        assertEquals(false, response.hasMore());
        assertEquals(2, response.rows().size());
        var first = (AnalyticsUnavailableVO.PolicyDrilldownRow) response.rows().get(0);
        assertEquals(11L, first.id());
        assertEquals("verified", first.evidenceStatus());
        assertEquals("/policies/11", first.detailHref());
    }

    @Test
    void overviewDoesNotPresentNonCanonicalCountsAsGreenMetrics() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        when(cases.countEligibleAnalyticsRecords()).thenReturn(18L);
        when(policies.countEligibleAnalyticsRecords()).thenReturn(57L);
        when(sources.countEligibleAnalyticsRecords()).thenReturn(121L);
        when(cases.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        when(cases.selectEligibleIndustryAnalyticsVersionStamps()).thenReturn(List.of());
        when(policies.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        when(sources.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        AnalyticsOverviewService service = new AnalyticsOverviewService(
                cases, policies, sources, new ObjectMapper());

        var response = service.overview(owner);

        assertEquals("partial", response.status());
        assertEquals("overview.verified_cases", response.cards().get(0).metricId());
        assertEquals(null, response.cards().get(0).value());
        assertEquals("Red", response.cards().get(0).readiness());
        assertEquals("案例尚未具备独立 canonical_case_id，不能发布已核验案例总数。",
                response.cards().get(0).caveat());
        assertEquals("Green", response.cards().get(1).readiness());
        assertEquals("Yellow", response.cards().get(2).readiness());
        assertEquals("来源尚未具备 canonical_source_id，当前按已核验来源记录 ID 去重。",
                response.cards().get(2).caveat());
        assertEquals("overview.covered_technologies", response.cards().get(3).metricId());
        assertEquals("Red", response.cards().get(3).readiness());
        assertEquals("正式技术 taxonomy 尚未完成审核。", response.cards().get(3).caveat());
    }

    @Test
    void returnsStableEligibleIndustryBucketsWithSampleAndMissingCounts() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        when(cases.countEligibleAnalyticsRecords()).thenReturn(6L);
        when(policies.countEligibleAnalyticsRecords()).thenReturn(2L);
        when(sources.countEligibleAnalyticsRecords()).thenReturn(3L);
        when(cases.countIndustryTaxonomyTags()).thenReturn(2L);
        when(cases.countEligibleIndustryTaggedCases(List.of())).thenReturn(5L);
        when(cases.selectEligibleIndustryAnalyticsRows(List.of())).thenReturn(List.of(
                new CaseItemMapper.AnalyticsIndustryRow(7L, "人工智能服务", 4L),
                new CaseItemMapper.AnalyticsIndustryRow(9L, "企业服务", 2L)
        ));
        when(cases.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of("101:1:9001:4"));
        when(cases.selectEligibleIndustryAnalyticsVersionStamps()).thenReturn(List.of("101:7:1", "102:9:2"));
        when(policies.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of("2001:2:9002:1"));
        when(sources.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of("9001:4", "9002:1", "9003:5"));
        AnalyticsOverviewService service = new AnalyticsOverviewService(cases, policies, sources, new ObjectMapper());

        var response = service.industries(owner, "industry.case_count", List.of());

        assertEquals("industry.case_count", response.metric().metricId());
        assertEquals("industry:7", response.buckets().get(0).bucketId());
        assertEquals(4L, response.buckets().get(0).value());
        assertEquals("Yellow", response.metric().readiness());
        assertEquals("Yellow", response.buckets().get(0).readiness());
        assertEquals("Red", response.buckets().get(1).readiness());
        assertEquals(false, response.buckets().get(0).drilldown().available());
        assertEquals("/api/analytics/drilldown", response.buckets().get(0).drilldown().href());
        assertEquals(5L, response.sampleSize());
        assertEquals(1L, response.missingCount());
        assertEquals(6L, response.totalEligible());
        assertEquals("partial", response.status());
        assertEquals("CANONICAL_CASE_DEDUPLICATION_NOT_READY", response.caveats().get(0).code());
    }

    @Test
    void rebuildsAnIndustrySnapshotOnlyFromStableServerBucketIds() throws Exception {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        when(cases.countEligibleAnalyticsRecords()).thenReturn(4L);
        when(policies.countEligibleAnalyticsRecords()).thenReturn(0L);
        when(sources.countEligibleAnalyticsRecords()).thenReturn(4L);
        when(cases.countIndustryTaxonomyTags()).thenReturn(1L);
        when(cases.selectApprovedIndustryTagIds(List.of(7L))).thenReturn(List.of(7L));
        when(cases.countEligibleIndustryTaggedCases(List.of())).thenReturn(4L);
        when(cases.countEligibleIndustryTaggedCases(List.of(7L))).thenReturn(4L);
        when(cases.selectEligibleIndustryAnalyticsRows(List.of(7L))).thenReturn(List.of(
                new CaseItemMapper.AnalyticsIndustryRow(7L, "人工智能服务", 4L)
        ));
        when(cases.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of("101:1:9001:4"));
        when(cases.selectEligibleIndustryAnalyticsVersionStamps()).thenReturn(List.of("101:7:1"));
        when(policies.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        when(sources.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of("9001:4"));
        AnalyticsOverviewService service = new AnalyticsOverviewService(cases, policies, sources, new ObjectMapper());
        var filters = new ObjectMapper().createObjectNode().put("industryTagId", 7L);

        AnalyticsSnapshotMaterial material = service.rebuildSnapshot(
                "industry.case_count", filters, List.of("industry:7"));

        assertEquals("industry.case_count", material.metricId());
        assertEquals(List.of("industry:7"), material.allowedBucketIds());
        assertEquals(7L, new ObjectMapper().readTree(material.filtersJson()).path("industryTagId").asLong());
        assertEquals("industry:7", new ObjectMapper().readTree(material.payloadJson())
                .path("buckets").get(0).path("bucketId").asText());
    }

    @Test
    void rejectsAnIndustryFilterOutsideTheApprovedTaxonomyAllowlist() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        when(cases.selectApprovedIndustryTagIds(List.of(7L, 9L))).thenReturn(List.of(7L));
        AnalyticsOverviewService service = new AnalyticsOverviewService(
                cases, mock(PolicyMapper.class), mock(SourceMapper.class), new ObjectMapper());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.industries(owner, "industry.case_count", List.of(9L, 7L)));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("ANALYTICS_INVALID_FILTER", exception.getMessage());
    }

    @Test
    void refusesToCreateAnAnalyticsSnapshotWhenEvidenceVersionCannotBeVerified() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        when(cases.countEligibleAnalyticsRecords()).thenReturn(1L);
        when(policies.countEligibleAnalyticsRecords()).thenReturn(2L);
        when(sources.countEligibleAnalyticsRecords()).thenReturn(3L);
        when(cases.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of("101:1:9001:4"));
        when(policies.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of("2001:2:9002:1"));
        when(sources.selectEligibleAnalyticsVersionStamps())
                .thenThrow(new IllegalStateException("source revision read failed"));
        AnalyticsOverviewService service = new AnalyticsOverviewService(cases, policies, sources, new ObjectMapper());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.rebuildSnapshot(
                "overview.verified_cases", new ObjectMapper().createObjectNode(),
                List.of()));

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("ANALYTICS_DATA_VERSION_UNAVAILABLE", exception.getMessage());
    }

    @Test
    void rebuildsAnOverviewSnapshotWithoutSyntheticBuckets() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        when(cases.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        when(cases.selectEligibleIndustryAnalyticsVersionStamps()).thenReturn(List.of());
        when(policies.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        when(sources.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        AnalyticsOverviewService service = new AnalyticsOverviewService(cases, policies, sources, new ObjectMapper());

        AnalyticsSnapshotMaterial material = service.rebuildSnapshot(
                "overview.verified_cases", new ObjectMapper().createObjectNode(), List.of());

        assertEquals(List.of(), material.allowedBucketIds());
    }

    @Test
    void rejectsASyntheticBucketWhenRebuildingAnOverviewSnapshot() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        when(cases.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        when(cases.selectEligibleIndustryAnalyticsVersionStamps()).thenReturn(List.of());
        when(policies.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        when(sources.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        AnalyticsOverviewService service = new AnalyticsOverviewService(cases, policies, sources, new ObjectMapper());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.rebuildSnapshot(
                "overview.verified_cases", new ObjectMapper().createObjectNode(),
                List.of("overview.verified_cases")));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("ANALYTICS_INVALID_FILTER", exception.getMessage());
    }

    @Test
    void dataVersionChangesWhenEligibleEvidenceRevisionChangesWithoutChangingCounts() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        when(cases.countEligibleAnalyticsRecords()).thenReturn(1L);
        when(policies.countEligibleAnalyticsRecords()).thenReturn(2L);
        when(sources.countEligibleAnalyticsRecords()).thenReturn(3L);
        when(cases.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of("101:1:9001:4"));
        when(policies.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of("2001:2:9002:1"));
        when(sources.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of("9001:4", "9002:1", "9003:5"));
        AnalyticsOverviewService service = new AnalyticsOverviewService(cases, policies, sources, new ObjectMapper());

        AnalyticsSnapshotMaterial before = service.rebuildSnapshot(
                "overview.verified_cases", new ObjectMapper().createObjectNode(),
                List.of());
        when(sources.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of("9001:5", "9002:1", "9003:5"));
        AnalyticsSnapshotMaterial after = service.rebuildSnapshot(
                "overview.verified_cases", new ObjectMapper().createObjectNode(),
                List.of());

        assertEquals("overview.verified_cases", before.metricId());
        assertNotEquals(before.dataVersion(), after.dataVersion());
    }

    private void stubAnalyticsVersion(
            CaseItemMapper cases,
            PolicyMapper policies,
            SourceMapper sources,
            long caseCount,
            long policyCount,
            long sourceCount
    ) {
        when(cases.countEligibleAnalyticsRecords()).thenReturn(caseCount);
        when(policies.countEligibleAnalyticsRecords()).thenReturn(policyCount);
        when(sources.countEligibleAnalyticsRecords()).thenReturn(sourceCount);
        when(cases.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        when(cases.selectEligibleIndustryAnalyticsVersionStamps()).thenReturn(List.of());
        when(policies.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
        when(sources.selectEligibleAnalyticsVersionStamps()).thenReturn(List.of());
    }

    private PolicyMapper.AnalyticsPolicyRow policyRow(
            long id,
            LocalDate publishDate,
            long regionId,
            String regionName
    ) {
        return new PolicyMapper.AnalyticsPolicyRow(
                id, "政策 " + id, publishDate, regionId, 1L, "province", regionName,
                regionId, regionName,
                "发布机关", "funding_subsidy", "verified",
                LocalDateTime.of(2026, 3, 31, 9, 0));
    }
}
