package com.opc.platform.ai.controller;

import com.opc.platform.ai.config.AiWebMvcConfig;
import com.opc.platform.common.config.SecurityConfig;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
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
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringJUnitWebConfig(AiCapabilitiesControllerTest.TestWebConfig.class)
class AiCapabilitiesControllerTest {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private AiClient aiClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(userAuthService, aiClient);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousUserCannotReadAiCapabilities() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "Please sign in"))
                .when(userAuthService).getCurrentUser(null);

        mockMvc.perform(get("/api/ai/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void authenticatedUserCanReadSafeAiCapabilities() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("ACha_");
        user.setEmail("acha@example.com");
        when(userAuthService.getCurrentUser("valid-user-token")).thenReturn(user);
        when(aiClient.descriptor()).thenReturn(
                new AiProviderDescriptor("fake", "contract-test-model", true)
        );

        mockMvc.perform(get("/api/ai/capabilities")
                        .header("Authorization", "Bearer valid-user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.provider.provider").value("fake"))
                .andExpect(jsonPath("$.data.provider.model").value("contract-test-model"))
                .andExpect(jsonPath("$.data.provider.available").value(true))
                .andExpect(jsonPath("$.data.capabilities[0].id").value("case-analysis"))
                .andExpect(jsonPath("$.data.capabilities[0].version").value("case-analysis-v1"))
                .andExpect(jsonPath("$.data.capabilities[0].available").value(true))
                .andExpect(jsonPath("$.data.capabilities[1].id").value("entrepreneurship-advisor"))
                .andExpect(jsonPath("$.data.capabilities[1].version").value("entrepreneurship-advisor-v1"))
                .andExpect(jsonPath("$.data.capabilities[1].available").value(true))
                .andExpect(jsonPath("$.data.provider.apiKey").doesNotExist());
    }

    @Configuration
    @EnableWebMvc
    @Import({
            AiWebMvcConfig.class,
            SecurityConfig.class,
            UserAuthInterceptor.class,
            AiCapabilitiesController.class,
            GlobalExceptionHandler.class
    })
    static class TestWebConfig {

        @Bean
        UserAuthService userAuthService() {
            return mock(UserAuthService.class);
        }

        @Bean
        AiClient aiClient() {
            return mock(AiClient.class);
        }
    }
}
