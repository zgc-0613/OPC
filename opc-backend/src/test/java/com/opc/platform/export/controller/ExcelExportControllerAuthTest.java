package com.opc.platform.export.controller;

import com.opc.platform.adminauth.AdminAuthInterceptor;
import com.opc.platform.adminauth.entity.AdminAccount;
import com.opc.platform.adminauth.service.AdminAuthService;
import com.opc.platform.common.config.WebMvcConfig;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.common.exception.GlobalExceptionHandler;
import com.opc.platform.export.service.ExcelExportService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(ExcelExportControllerAuthTest.TestWebConfig.class)
class ExcelExportControllerAuthTest {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private ExcelExportService excelExportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(adminAuthService, excelExportService);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void anonymousPolicyExportRequiresAdministratorSession() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "管理员登录状态无效"))
                .when(adminAuthService).requireAccount(null);

        mockMvc.perform(get("/api/admin/export/policies.xlsx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        verify(excelExportService, never()).exportPolicies(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authenticatedPolicyExportReachesExportService() throws Exception {
        AdminAccount account = new AdminAccount();
        account.setId(7L);
        account.setUsername("ACha_");
        when(adminAuthService.requireAccount("valid-admin-token")).thenReturn(account);

        mockMvc.perform(get("/api/admin/export/policies.xlsx")
                        .header("X-Admin-Token", "valid-admin-token"))
                .andExpect(status().isOk());

        verify(adminAuthService).requireAccount("valid-admin-token");
        verify(excelExportService).exportPolicies(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authenticatedPaperDatasetExportReachesExportService() throws Exception {
        AdminAccount account = new AdminAccount();
        account.setId(7L);
        account.setUsername("ACha_");
        when(adminAuthService.requireAccount("valid-admin-token")).thenReturn(account);

        mockMvc.perform(get("/api/admin/export/paper-dataset.xlsx")
                        .header("X-Admin-Token", "valid-admin-token"))
                .andExpect(status().isOk());

        verify(excelExportService).exportPaperDataset(org.mockito.ArgumentMatchers.any());
    }

    @Configuration
    @EnableWebMvc
    @Import({WebMvcConfig.class, AdminAuthInterceptor.class, GlobalExceptionHandler.class})
    static class TestWebConfig {

        @Bean
        AdminAuthService adminAuthService() {
            return mock(AdminAuthService.class);
        }

        @Bean
        ExcelExportService excelExportService() {
            return mock(ExcelExportService.class);
        }

        @Bean
        ExcelExportController excelExportController(ExcelExportService excelExportService) {
            return new ExcelExportController(excelExportService);
        }
    }
}
