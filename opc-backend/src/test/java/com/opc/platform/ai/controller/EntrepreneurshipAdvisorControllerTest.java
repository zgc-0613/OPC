package com.opc.platform.ai.controller;

import com.opc.platform.ai.config.AiWebMvcConfig;
import com.opc.platform.ai.exception.AiResponseValidationException;
import com.opc.platform.ai.service.AiIndustryClassificationService;
import com.opc.platform.ai.service.EntrepreneurshipAdvisorService;
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

@SpringJUnitWebConfig(EntrepreneurshipAdvisorControllerTest.TestWebConfig.class)
class EntrepreneurshipAdvisorControllerTest {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private EntrepreneurshipAdvisorService advisorService;

    @Autowired
    private AiIndustryClassificationService classificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(userAuthService, advisorService, classificationService);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousUserCannotRequestEntrepreneurshipAdvice() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"))
                .when(userAuthService).getCurrentUser(null);

        mockMvc.perform(post("/api/ai/entrepreneurship-advice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        verify(advisorService, never()).advise(any(), any());
    }

    @Test
    void authenticatedUserReachesBoundedAdvisorService() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("ACha_");
        user.setEmail("acha@example.com");
        when(userAuthService.getCurrentUser("valid-user-token")).thenReturn(user);
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "智能体模型尚未配置或未启用"))
                .when(advisorService).advise(any(), any());

        mockMvc.perform(post("/api/ai/entrepreneurship-advice")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503));

        verify(advisorService).advise(any(), any());
    }

    @Test
    void invalidFreeFormRequestIsRejectedBeforeProviderCall() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("ACha_");
        user.setEmail("acha@example.com");
        when(userAuthService.getCurrentUser("valid-user-token")).thenReturn(user);

        mockMvc.perform(post("/api/ai/entrepreneurship-advice")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ventureType\":\"anything\",\"industry\":\"\",\"goal\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(advisorService, never()).advise(any(), any());
    }

    @Test
    void truncatedModelResponseReturnsSafeDiagnosticCode() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("ACha_");
        user.setEmail("acha@example.com");
        when(userAuthService.getCurrentUser("valid-user-token")).thenReturn(user);
        doThrow(new AiResponseValidationException("TRUNCATED_RESPONSE"))
                .when(advisorService).advise(any(), any());

        mockMvc.perform(post("/api/ai/entrepreneurship-advice")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.data.diagnosticCode").value("TRUNCATED_RESPONSE"))
                .andExpect(jsonPath("$.message").value("模型输出被截断，请重试或联系管理员提高最大输出词元"));
    }

    @Test
    void authenticatedUserCanExplicitlyRequestIndustryClassification() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("ACha_");
        user.setEmail("acha@example.com");
        when(userAuthService.getCurrentUser("valid-user-token")).thenReturn(user);
        when(classificationService.classify(any(), any())).thenReturn(
                new com.opc.platform.tag.vo.IndustryResolution(
                        703L, "人工智能应用", "common", "ai", 0.55, true
                )
        );

        mockMvc.perform(post("/api/ai/industry-resolution")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"industry\":\"农业智能决策\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.method").value("ai"))
                .andExpect(jsonPath("$.data.requiresConfirmation").value(true));

        verify(classificationService).classify(any(), any());
    }

    private String validRequest() {
        return """
                {
                  "ventureType":"solo_company",
                  "regionId":3,
                  "industry":"人工智能应用",
                  "stage":"validation",
                  "budgetRange":"under_100k",
                  "goal":"验证首批付费客户",
                  "existingResources":"已有产品原型和两名行业顾问",
                  "userQuestion":"优先验证哪类客户？"
                }
                """;
    }

    @Configuration
    @EnableWebMvc
    @Import({
            AiWebMvcConfig.class,
            SecurityConfig.class,
            UserAuthInterceptor.class,
            EntrepreneurshipAdvisorController.class,
            GlobalExceptionHandler.class
    })
    static class TestWebConfig {

        @Bean
        UserAuthService userAuthService() {
            return mock(UserAuthService.class);
        }

        @Bean
        EntrepreneurshipAdvisorService entrepreneurshipAdvisorService() {
            return mock(EntrepreneurshipAdvisorService.class);
        }

        @Bean
        AiIndustryClassificationService aiIndustryClassificationService() {
            return mock(AiIndustryClassificationService.class);
        }
    }
}
