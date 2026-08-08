package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsOverviewServiceTest {

    private final AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");

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
}
