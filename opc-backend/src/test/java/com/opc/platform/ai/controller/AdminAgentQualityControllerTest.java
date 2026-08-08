package com.opc.platform.ai.controller;

import com.opc.platform.adminauth.AdminAuthInterceptor;
import com.opc.platform.adminauth.entity.AdminAccount;
import com.opc.platform.adminauth.service.AdminAuthService;
import com.opc.platform.ai.service.AdminAgentQualityService;
import com.opc.platform.ai.vo.AdminAgentQualitySummaryVO;
import com.opc.platform.ai.vo.AdminAgentQualityVO;
import com.opc.platform.common.config.SecurityConfig;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.common.exception.GlobalExceptionHandler;
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
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(AdminAgentQualityControllerTest.TestWebConfig.class)
class AdminAgentQualityControllerTest {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private AdminAgentQualityService qualityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(adminAuthService, qualityService);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousAndOrdinaryUserCannotReadQualityTelemetry() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "Admin session required"))
                .when(adminAuthService).requireAccount(null);

        mockMvc.perform(get("/api/admin/ai/research/quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/admin/ai/research/quality")
                        .header("Authorization", "Bearer ordinary-user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void authenticatedAdminReceivesOnlyAggregatedTelemetry() throws Exception {
        AdminAccount admin = new AdminAccount();
        admin.setId(7L);
        admin.setUsername("reviewer");
        when(adminAuthService.requireAccount("valid-admin-token")).thenReturn(admin);
        when(qualityService.quality(null, null, null, null, null, "day"))
                .thenReturn(new AdminAgentQualityVO(
                        3L, 2L, 1L, 0L, 0L, 0L,
                        1L, 0L, 1.0,
                        Map.of(), Map.of(), Map.of("PROVIDER_TIMEOUT", 1L),
                        List.of(), List.of(),
                        new AdminAgentQualitySummaryVO(300L, 100L),
                        new AdminAgentQualitySummaryVO(900L, 300L),
                        new AdminAgentQualitySummaryVO(6L, 2L),
                        "day", LocalDateTime.of(2026, 8, 2, 7, 0)
                ));

        mockMvc.perform(get("/api/admin/ai/research/quality")
                        .header("X-Admin-Token", "valid-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sampleSize").value(3))
                .andExpect(jsonPath("$.data.toolCallSummary.total").value(6))
                .andExpect(jsonPath("$.data.failureReasons.PROVIDER_TIMEOUT").value(1))
                .andExpect(jsonPath("$.data.question").doesNotExist())
                .andExpect(jsonPath("$.data.answer").doesNotExist())
                .andExpect(jsonPath("$.data.comment").doesNotExist());
    }

    @Configuration
    @EnableWebMvc
    @Import({
            SecurityConfig.class,
            AdminAuthInterceptor.class,
            AdminAgentQualityController.class,
            GlobalExceptionHandler.class
    })
    static class TestWebConfig implements WebMvcConfigurer {

        @Autowired
        private AdminAuthInterceptor adminAuthInterceptor;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api/admin/**");
        }

        @Bean
        AdminAuthService adminAuthService() {
            return mock(AdminAuthService.class);
        }

        @Bean
        AdminAgentQualityService adminAgentQualityService() {
            return mock(AdminAgentQualityService.class);
        }
    }
}
