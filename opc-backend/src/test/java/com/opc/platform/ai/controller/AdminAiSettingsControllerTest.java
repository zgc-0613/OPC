package com.opc.platform.ai.controller;

import com.opc.platform.adminauth.AdminAuthInterceptor;
import com.opc.platform.adminauth.entity.AdminAccount;
import com.opc.platform.adminauth.service.AdminAuthService;
import com.opc.platform.ai.service.AiSettingsService;
import com.opc.platform.ai.dto.AiModelOptionDTO;
import com.opc.platform.ai.vo.AiModelSettingsVO;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(AdminAiSettingsControllerTest.TestWebConfig.class)
class AdminAiSettingsControllerTest {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private AiSettingsService aiSettingsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(adminAuthService, aiSettingsService);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousAndNormalUserCannotUpdateAiSettings() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "Admin session required"))
                .when(adminAuthService).requireAccount(null);

        mockMvc.perform(put("/api/admin/ai-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(put("/api/admin/ai-settings")
                        .header("Authorization", "Bearer normal-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void authenticatedAdminCanUpdateButApiKeyIsNeverReturned() throws Exception {
        AdminAccount admin = new AdminAccount();
        admin.setId(7L);
        admin.setUsername("ACha_");
        when(adminAuthService.requireAccount("valid-admin-token")).thenReturn(admin);

        AiModelSettingsVO saved = new AiModelSettingsVO();
        saved.setProvider("deepseek");
        saved.setApiFormat("openai_compatible");
        saved.setApiBaseUrl("https://api.example.com/v1");
        saved.setModelId("configured-model-id");
        saved.setApiKeyConfigured(true);
        saved.setEnabled(false);
        when(aiSettingsService.update(any(), any())).thenReturn(saved);

        mockMvc.perform(put("/api/admin/ai-settings")
                        .header("X-Admin-Token", "valid-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
    }

    @Test
    void authenticatedAdminCanDiscoverModelsWithTransientFormSettings() throws Exception {
        AdminAccount admin = new AdminAccount();
        admin.setId(7L);
        admin.setUsername("ACha_");
        when(adminAuthService.requireAccount("valid-admin-token")).thenReturn(admin);
        when(aiSettingsService.discoverModels(any(), any())).thenReturn(List.of(
                new AiModelOptionDTO("model-a", "model-a"),
                new AiModelOptionDTO("model-b", "model-b")
        ));

        mockMvc.perform(post("/api/admin/ai-settings/models/discover")
                        .header("X-Admin-Token", "valid-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discoveryPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].modelId").value("model-a"))
                .andExpect(jsonPath("$.data[0].apiKey").doesNotExist());
    }

    private String validPayload() {
        return """
                {
                  "provider": "deepseek",
                  "apiFormat": "openai_compatible",
                  "apiBaseUrl": "https://api.example.com/v1",
                  "modelId": "configured-model-id",
                  "apiKey": "sk-never-return-this",
                  "temperature": 0.2,
                  "maxOutputTokens": 1200,
                  "timeoutSeconds": 30,
                  "retryCount": 1,
                  "dailyTokenQuota": 100000,
                  "enabled": false
                }
                """;
    }

    private String discoveryPayload() {
        return """
                {
                  "provider": "deepseek",
                  "apiFormat": "openai_compatible",
                  "apiBaseUrl": "https://api.example.com/v1",
                  "apiKey": "sk-transient-only",
                  "timeoutSeconds": 15
                }
                """;
    }

    @Configuration
    @EnableWebMvc
    @Import({
            SecurityConfig.class,
            AdminAuthInterceptor.class,
            AdminAiSettingsController.class,
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
        AiSettingsService aiSettingsService() {
            return mock(AiSettingsService.class);
        }
    }
}
