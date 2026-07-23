package com.opc.platform.ai.config;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.common.exception.GlobalExceptionHandler;
import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.AuthenticatedUser;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(AiWebMvcConfigTest.TestWebConfig.class)
class AiWebMvcConfigTest {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private UserAuthService userAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void anonymousAiRequestRequiresUserSession() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"))
                .when(userAuthService).getCurrentUser(null);

        mockMvc.perform(get("/api/ai/test-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void authenticatedAiRequestExposesMinimalUserIdentity() throws Exception {
        UserLoginVO login = new UserLoginVO();
        login.setUserId(42L);
        login.setUsername("ACha_");
        login.setEmail("acha@example.com");
        when(userAuthService.getCurrentUser("valid-user-token")).thenReturn(login);

        mockMvc.perform(get("/api/ai/test-session")
                        .header("Authorization", "Bearer valid-user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.username").value("ACha_"));

        verify(userAuthService).getCurrentUser("valid-user-token");
    }

    @Configuration
    @EnableWebMvc
    @Import({AiWebMvcConfig.class, UserAuthInterceptor.class, GlobalExceptionHandler.class})
    static class TestWebConfig {

        @Bean
        UserAuthService userAuthService() {
            return mock(UserAuthService.class);
        }

        @Bean
        TestAiController testAiController() {
            return new TestAiController();
        }
    }

    @RestController
    static class TestAiController {

        @GetMapping("/api/ai/test-session")
        Result<AuthenticatedUser> session(
                @RequestAttribute(UserAuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
        ) {
            return Result.success(user);
        }
    }
}
