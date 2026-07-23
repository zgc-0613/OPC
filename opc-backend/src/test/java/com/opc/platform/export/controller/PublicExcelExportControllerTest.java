package com.opc.platform.export.controller;

import com.opc.platform.export.service.ExcelExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicExcelExportControllerTest {

    @Mock
    private ExcelExportService excelExportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PublicExcelExportController(excelExportService)
        ).build();
    }

    @Test
    void publicPolicyExportUsesPublishedOnlyService() throws Exception {
        mockMvc.perform(get("/api/public/export/policies.xlsx"))
                .andExpect(status().isOk());

        verify(excelExportService).exportPublishedPolicies(any());
    }
}
