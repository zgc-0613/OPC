package com.opc.platform.ai.controller;

import com.opc.platform.ai.config.AiWebMvcConfig;
import com.opc.platform.ai.service.AnalyticsResearchStartReceipt;
import com.opc.platform.ai.service.AnalyticsResearchStartService;
import com.opc.platform.common.config.SecurityConfig;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(AnalyticsResearchControllerTest.TestWebConfig.class)
class AnalyticsResearchControllerTest {

    @Autowired private WebApplicationContext applicationContext;
    @Autowired private UserAuthService userAuthService;
    @Autowired private AnalyticsResearchStartService analyticsResearchStartService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(userAuthService, analyticsResearchStartService);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
    }

    @Test
    void authenticatedUserStartsAResearchRunFromARebuiltAnalyticsSnapshot() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L); user.setUsername("owner"); user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);
        when(analyticsResearchStartService.start(any(), any())).thenReturn(
                new AnalyticsResearchStartReceipt(null, 501L, 91L, "received",
                        17L, "overview.verified_cases", "analytics-v1:current"));

        mockMvc.perform(post("/api/ai/research/from-analytics")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metricId":"overview.verified_cases","filters":{},
                                 "selectedBucketIds":[],
                                 "dataVersion":"analytics-v1:current",
                                 "userQuestion":"请说明下一步研究方向", "idempotencyKey":"analytics-start-1"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.analyticsSnapshotId").value(17))
                .andExpect(jsonPath("$.data.dataVersion").value("analytics-v1:current"));
        verify(analyticsResearchStartService).start(any(), any());
    }

    @Test
    void anonymousRequestNeverReachesTheAnalyticsResearchService() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "UNAUTHORIZED"))
                .when(userAuthService).getCurrentUser(null);

        mockMvc.perform(post("/api/ai/research/from-analytics")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        verify(analyticsResearchStartService, never()).start(any(), any());
    }

    @Configuration
    @EnableWebMvc
    @Import({
            AiWebMvcConfig.class,
            SecurityConfig.class,
            UserAuthInterceptor.class,
            AnalyticsResearchController.class,
            GlobalExceptionHandler.class
    })
    static class TestWebConfig {
        @Bean UserAuthService userAuthService() { return mock(UserAuthService.class); }
        @Bean AnalyticsResearchStartService analyticsResearchStartService() { return mock(AnalyticsResearchStartService.class); }
    }
}
