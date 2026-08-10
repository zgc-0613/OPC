package com.opc.platform.ai.controller;

import com.opc.platform.ai.config.AiWebMvcConfig;
import com.opc.platform.ai.service.AnalyticsOverviewService;
import com.opc.platform.ai.vo.AnalyticsIndustriesVO;
import com.opc.platform.common.config.SecurityConfig;
import com.opc.platform.common.exception.GlobalExceptionHandler;
import com.opc.platform.userauth.UserAuthInterceptor;
import com.opc.platform.userauth.service.UserAuthService;
import com.opc.platform.userauth.vo.UserLoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(AnalyticsControllerTest.TestWebConfig.class)
class AnalyticsControllerTest {

    @Autowired private WebApplicationContext applicationContext;
    @Autowired private UserAuthService userAuthService;
    @Autowired private AnalyticsOverviewService analyticsOverviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(userAuthService, analyticsOverviewService);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
    }

    @Test
    void authenticatedUserReadsTheNormalizedIndustryMetricContract() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L); user.setUsername("owner"); user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);
        when(analyticsOverviewService.industries(any(), eq("industry.case_count"), eq(List.of(7L))))
                .thenReturn(response());

        mockMvc.perform(get("/api/analytics/industries")
                        .header("Authorization", "Bearer valid-token")
                        .param("metricId", "industry.case_count")
                        .param("industryTagIds", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.metric.metricId").value("industry.case_count"))
                .andExpect(jsonPath("$.data.buckets[0].bucketId").value("industry:7"))
                .andExpect(jsonPath("$.data.dataVersion").value("analytics-v1:test"));
        verify(analyticsOverviewService).industries(any(), eq("industry.case_count"), eq(List.of(7L)));
    }

    private AnalyticsIndustriesVO response() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-01T10:00:00+08:00");
        return new AnalyticsIndustriesVO(
                List.of(new AnalyticsIndustriesVO.Bucket(
                        "industry:7", 7L, "人工智能服务", 4L, new BigDecimal("0.5"),
                        4L, 0L, "Yellow", new AnalyticsIndustriesVO.Drilldown(
                        "/api/analytics/drilldown", List.of("case"), false))),
                new AnalyticsIndustriesVO.Metric(
                        "industry.case_count", "行业案例数量", "industry-v1", "Yellow",
                        "已核验案例数量", "条", true),
                new AnalyticsIndustriesVO.Filters(List.of(7L)),
                4L, 0L, 8L, now, "analytics-v1:test",
                new AnalyticsIndustriesVO.Freshness(now, now, 0, "current"),
                List.of(), "partial");
    }

    @Configuration
    @EnableWebMvc
    @Import({
            AiWebMvcConfig.class,
            SecurityConfig.class,
            UserAuthInterceptor.class,
            AnalyticsController.class,
            GlobalExceptionHandler.class
    })
    static class TestWebConfig {
        @Bean UserAuthService userAuthService() { return mock(UserAuthService.class); }
        @Bean AnalyticsOverviewService analyticsOverviewService() { return mock(AnalyticsOverviewService.class); }
    }
}
