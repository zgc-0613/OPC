package com.opc.platform.ai.controller;

import com.opc.platform.ai.config.AiWebMvcConfig;
import com.opc.platform.ai.service.CaseAnalysisService;
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

@SpringJUnitWebConfig(CaseAnalysisControllerTest.TestWebConfig.class)
class CaseAnalysisControllerTest {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private CaseAnalysisService analysisService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(userAuthService, analysisService);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousAndDisabledUsersCannotAnalyzeCases() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"))
                .when(userAuthService).getCurrentUser(null);

        mockMvc.perform(post("/api/ai/case-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效"))
                .when(userAuthService).getCurrentUser("disabled-user-token");
        mockMvc.perform(post("/api/ai/case-analysis")
                        .header("Authorization", "Bearer disabled-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        verify(analysisService, never()).analyze(any(), any());
    }

    @Test
    void authenticatedUserReachesBoundedCaseAnalysisService() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("ACha_");
        user.setEmail("acha@example.com");
        when(userAuthService.getCurrentUser("valid-user-token")).thenReturn(user);
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "智能体模型尚未配置或未启用"))
                .when(analysisService).analyze(any(), any());

        mockMvc.perform(post("/api/ai/case-analysis")
                        .header("Authorization", "Bearer valid-user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseId\":1,\"userQuestion\":\"技术风险是什么？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503));
    }

    @Configuration
    @EnableWebMvc
    @Import({
            AiWebMvcConfig.class,
            SecurityConfig.class,
            UserAuthInterceptor.class,
            CaseAnalysisController.class,
            GlobalExceptionHandler.class
    })
    static class TestWebConfig {

        @Bean
        UserAuthService userAuthService() {
            return mock(UserAuthService.class);
        }

        @Bean
        CaseAnalysisService caseAnalysisService() {
            return mock(CaseAnalysisService.class);
        }
    }
}
