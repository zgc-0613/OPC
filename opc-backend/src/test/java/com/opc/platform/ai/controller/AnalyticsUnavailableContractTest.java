package com.opc.platform.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.exception.GlobalExceptionHandler;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.ai.service.AnalyticsOverviewService;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static com.opc.platform.userauth.UserAuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AnalyticsUnavailableContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        when(cases.countEligibleAnalyticsRecords()).thenReturn(0L);
        when(policies.countEligibleAnalyticsRecords()).thenReturn(0L);
        when(sources.countEligibleAnalyticsRecords()).thenReturn(0L);
        when(cases.selectEligibleAnalyticsVersionStamps()).thenReturn(java.util.List.of());
        when(cases.selectEligibleIndustryAnalyticsVersionStamps()).thenReturn(java.util.List.of());
        when(policies.selectEligibleAnalyticsVersionStamps()).thenReturn(java.util.List.of());
        when(sources.selectEligibleAnalyticsVersionStamps()).thenReturn(java.util.List.of());
        when(cases.selectEligibleIndustryAnalyticsLastUpdatedAt()).thenReturn(null);
        AnalyticsOverviewService service = new AnalyticsOverviewService(
                cases, policies, sources, new ObjectMapper());
        mockMvc = standaloneSetup(new AnalyticsController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void technologyTaxonomyGapIsAnExplicitUnavailableResponse() throws Exception {
        mockMvc.perform(get("/api/analytics/technologies")
                        .requestAttr(AUTHENTICATED_USER_ATTRIBUTE,
                                new AuthenticatedUser(42L, "owner", "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("unavailable"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.unavailableReason")
                        .value("TECHNOLOGY_TAXONOMY_NOT_READY"))
                .andExpect(jsonPath("$.data.verifiedOnly").value(true))
                .andExpect(jsonPath("$.data.dataFreshness.status").value("unknown"))
                .andExpect(jsonPath("$.data.coverage.eligible").value(0))
                .andExpect(jsonPath("$.data.rows").isEmpty())
                .andExpect(jsonPath("$.data.data").isArray())
                .andExpect(jsonPath("$.data.data").isEmpty())
                .andExpect(jsonPath("$.data.metric.readiness").value("Red"))
                .andExpect(jsonPath("$.data.caveats[0].code")
                        .value("TECHNOLOGY_TAXONOMY_NOT_READY"));
    }

    @Test
    void revenueSchemaGapIsUnavailableEvenWithACompleteComparabilityGroup() throws Exception {
        mockMvc.perform(get("/api/analytics/revenue")
                        .param("metricId", "revenue.range_distribution")
                        .param("currency", "CNY")
                        .param("revenuePeriod", "annual")
                        .param("revenueType", "revenue")
                        .requestAttr(AUTHENTICATED_USER_ATTRIBUTE,
                                new AuthenticatedUser(42L, "owner", "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("unavailable"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.unavailableReason")
                        .value("REVENUE_SCHEMA_NOT_READY"))
                .andExpect(jsonPath("$.data.rows").isEmpty())
                .andExpect(jsonPath("$.data.data").isEmpty())
                .andExpect(jsonPath("$.data.metric.readiness").value("Red"))
                .andExpect(jsonPath("$.data.filters.currency").value("CNY"))
                .andExpect(jsonPath("$.data.caveats[0].code")
                        .value("REVENUE_SCHEMA_NOT_READY"));
    }

    @Test
    void caseRegionRoleGapIsUnavailableInsteadOfBeingReportedAsAnEmptyDistribution() throws Exception {
        mockMvc.perform(get("/api/analytics/regions")
                        .param("metricId", "region.case_count")
                        .requestAttr(AUTHENTICATED_USER_ATTRIBUTE,
                                new AuthenticatedUser(42L, "owner", "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("unavailable"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.unavailableReason")
                        .value("CASE_REGION_ROLE_NOT_READY"))
                .andExpect(jsonPath("$.data.rows").isEmpty())
                .andExpect(jsonPath("$.data.data").isEmpty())
                .andExpect(jsonPath("$.data.metric.readiness").value("Red"))
                .andExpect(jsonPath("$.data.caveats[0].code")
                        .value("CASE_REGION_ROLE_NOT_READY"));
    }

    @Test
    void caseBusinessTimeTrendIsUnavailableWithoutARealBusinessTimeField() throws Exception {
        mockMvc.perform(get("/api/analytics/trends")
                        .param("metricId", "trend.case_business_time")
                        .requestAttr(AUTHENTICATED_USER_ATTRIBUTE,
                                new AuthenticatedUser(42L, "owner", "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("unavailable"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.unavailableReason")
                        .value("CASE_BUSINESS_TIME_NOT_READY"))
                .andExpect(jsonPath("$.data.series").isEmpty())
                .andExpect(jsonPath("$.data.data").isEmpty())
                .andExpect(jsonPath("$.data.metric.metricId").value("trend.case_business_time"))
                .andExpect(jsonPath("$.data.metric.readiness").value("Red"))
                .andExpect(jsonPath("$.data.caveats[0].code")
                        .value("CASE_BUSINESS_TIME_NOT_READY"));
    }

    @Test
    void drilldownDoesNotFallBackToAPublishedOnlyWorkspaceQuery() throws Exception {
        mockMvc.perform(get("/api/analytics/drilldown")
                        .param("metricId", "industry.case_count")
                        .param("dataVersion", "analytics-v1:requested")
                        .param("entityType", "case")
                        .param("bucketId", "industry:7")
                        .requestAttr(AUTHENTICATED_USER_ATTRIBUTE,
                                new AuthenticatedUser(42L, "owner", "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("unavailable"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.unavailableReason")
                        .value("ANALYTICS_DRILLDOWN_NOT_READY"))
                .andExpect(jsonPath("$.data.rows").isEmpty())
                .andExpect(jsonPath("$.data.data").isEmpty())
                .andExpect(jsonPath("$.data.dataVersion").value("analytics-v1:requested"))
                .andExpect(jsonPath("$.data.drilldown.available").value(false))
                .andExpect(jsonPath("$.data.caveats[0].code")
                        .value("ANALYTICS_DRILLDOWN_NOT_READY"));
    }

    @Test
    void overviewAndIndustriesExposeTheSameReadContract() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")
                        .requestAttr(AUTHENTICATED_USER_ATTRIBUTE,
                                new AuthenticatedUser(42L, "owner", "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.dataFreshness").exists())
                .andExpect(jsonPath("$.data.verifiedOnly").value(true))
                .andExpect(jsonPath("$.data.coverage").exists())
                .andExpect(jsonPath("$.data.filters").isMap())
                .andExpect(jsonPath("$.data.rows").isArray())
                .andExpect(jsonPath("$.data.series").isArray());

        mockMvc.perform(get("/api/analytics/industries")
                        .requestAttr(AUTHENTICATED_USER_ATTRIBUTE,
                                new AuthenticatedUser(42L, "owner", "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").exists())
                .andExpect(jsonPath("$.data.dataFreshness").exists())
                .andExpect(jsonPath("$.data.verifiedOnly").value(true))
                .andExpect(jsonPath("$.data.coverage").exists())
                .andExpect(jsonPath("$.data.filters").isMap())
                .andExpect(jsonPath("$.data.rows").isArray())
                .andExpect(jsonPath("$.data.series").isArray());
    }
}
