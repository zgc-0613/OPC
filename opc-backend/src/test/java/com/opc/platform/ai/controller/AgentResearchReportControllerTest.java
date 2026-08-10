package com.opc.platform.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.config.AiWebMvcConfig;
import com.opc.platform.ai.entity.AgentResearchReport;
import com.opc.platform.ai.mapper.AgentResearchReportMapper;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.service.AgentResearchReportService;
import com.opc.platform.ai.service.AgentRunEvidenceService;
import com.opc.platform.common.config.SecurityConfig;
import com.opc.platform.common.exception.GlobalExceptionHandler;
import com.opc.platform.source.mapper.SourceMapper;
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

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(AgentResearchReportControllerTest.TestWebConfig.class)
class AgentResearchReportControllerTest {

    @Autowired private WebApplicationContext applicationContext;
    @Autowired private UserAuthService userAuthService;
    @Autowired private AgentResearchReportMapper reportMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(userAuthService, reportMapper);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity()).build();
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);
    }

    @Test
    void authenticatedOwnerCanExportMarkdownAndHtmlWithAttachmentHeaders() throws Exception {
        when(reportMapper.selectOwned(71L, 42L)).thenReturn(activeReport());

        mockMvc.perform(get("/api/ai/research/reports/71/export")
                        .header("Authorization", "Bearer valid-token")
                        .param("format", "markdown"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_MARKDOWN))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''research-report.md"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Saved answer")));

        mockMvc.perform(get("/api/ai/research/reports/71/export")
                        .header("Authorization", "Bearer valid-token")
                        .param("format", "html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''research-report.html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Saved answer")));
    }

    @Test
    void authenticatedOwnerCanExportARealPdfAttachment() throws Exception {
        when(reportMapper.selectOwned(71L, 42L)).thenReturn(activeReport());

        byte[] pdf = mockMvc.perform(get("/api/ai/research/reports/71/export")
                        .header("Authorization", "Bearer valid-token")
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename*=UTF-8''research-report.pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertTrue(pdf.length > 1_000);
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
        verify(reportMapper).selectOwned(71L, 42L);
    }

    @Test
    void pdfExportDoesNotBypassOwnerOrTrashChecks() throws Exception {
        AgentResearchReport trashed = activeReport();
        trashed.setStatus("trash");
        when(reportMapper.selectOwned(72L, 42L)).thenReturn(null);
        when(reportMapper.selectOwned(73L, 42L)).thenReturn(trashed);

        mockMvc.perform(get("/api/ai/research/reports/72/export")
                        .header("Authorization", "Bearer valid-token")
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(get("/api/ai/research/reports/73/export")
                        .header("Authorization", "Bearer valid-token")
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void anonymousAndUnknownFormatRequestsNeverExportAReport() throws Exception {
        doThrow(new com.opc.platform.common.exception.BusinessException(
                com.opc.platform.common.enums.ErrorCode.UNAUTHORIZED, "UNAUTHORIZED"))
                .when(userAuthService).getCurrentUser(null);

        mockMvc.perform(get("/api/ai/research/reports/71/export").param("format", "markdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        verify(reportMapper, never()).selectOwned(any(), any());

        mockMvc.perform(get("/api/ai/research/reports/71/export")
                        .header("Authorization", "Bearer valid-token")
                        .param("format", "docx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    private AgentResearchReport activeReport() {
        AgentResearchReport report = new AgentResearchReport();
        report.setId(71L);
        report.setUserId(42L);
        report.setStatus("active");
        report.setTitle("Saved report");
        report.setResultJson("{\"structuredResult\":{\"directAnswer\":\"Saved answer\"}}");
        report.setCitationManifestJson("[]");
        report.setEvidenceVersion("sha256:test");
        report.setCreatedAt(LocalDateTime.of(2026, 8, 2, 10, 15));
        return report;
    }

    @Configuration
    @EnableWebMvc
    @Import({
            AiWebMvcConfig.class,
            SecurityConfig.class,
            UserAuthInterceptor.class,
            AgentResearchReportController.class,
            GlobalExceptionHandler.class
    })
    static class TestWebConfig {
        @Bean UserAuthService userAuthService() { return mock(UserAuthService.class); }
        @Bean AiAnalysisRunMapper aiAnalysisRunMapper() { return mock(AiAnalysisRunMapper.class); }
        @Bean AiAgentMessageMapper aiAgentMessageMapper() { return mock(AiAgentMessageMapper.class); }
        @Bean AgentResearchReportMapper agentResearchReportMapper() { return mock(AgentResearchReportMapper.class); }
        @Bean SourceMapper sourceMapper() { return mock(SourceMapper.class); }
        @Bean AgentRunEvidenceService agentRunEvidenceService() { return mock(AgentRunEvidenceService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean AgentResearchReportService agentResearchReportService(
                AiAnalysisRunMapper runs,
                AiAgentMessageMapper messages,
                AgentResearchReportMapper reports,
                ObjectMapper objectMapper,
                SourceMapper sources,
                AgentRunEvidenceService evidence
        ) {
            return new AgentResearchReportService(runs, messages, reports, objectMapper, sources, evidence);
        }
    }
}
