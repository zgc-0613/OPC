package com.opc.platform.ai.controller;

import com.opc.platform.ai.config.AiWebMvcConfig;
import com.opc.platform.ai.dto.AgentSessionStartDTO;
import com.opc.platform.ai.service.AgentResearchReceipt;
import com.opc.platform.ai.service.AgentResearchStartReceipt;
import com.opc.platform.ai.exception.AgentHistoryCursorStaleException;
import com.opc.platform.ai.service.AgentResearchQueryService;
import com.opc.platform.ai.service.AgentResearchBranchService;
import com.opc.platform.ai.service.AgentResearchService;
import com.opc.platform.ai.service.AgentRunEvidenceService;
import com.opc.platform.ai.service.AgentSessionHistoryService;
import com.opc.platform.ai.vo.AgentSessionHistoryPageVO;
import com.opc.platform.ai.vo.AgentSessionVO;
import com.opc.platform.ai.vo.AgentResearchBranchMaterialVO;
import com.opc.platform.common.config.SecurityConfig;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.common.exception.GlobalExceptionHandler;
import com.opc.platform.userauth.UserAuthInterceptor;
import com.opc.platform.userauth.service.UserAuthService;
import com.opc.platform.userauth.vo.UserLoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(AgentResearchControllerTest.TestWebConfig.class)
class AgentResearchControllerTest {

    @Autowired private WebApplicationContext applicationContext;
    @Autowired private UserAuthService userAuthService;
    @Autowired private AgentResearchService researchService;
    @Autowired private AgentResearchBranchService branchService;
    @Autowired private AgentSessionHistoryService historyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(userAuthService, researchService, branchService, historyService);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity()).build();
    }

    @Test
    void authenticatedMessageSubmissionReturnsAcceptedRunReceipt() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);
        when(researchService.submit(any(), anyLong(), any()))
                .thenReturn(new AgentResearchReceipt(10L, 20L, 30L, "received"));

        mockMvc.perform(post("/api/ai/research/sessions/10/messages")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Research Hubei AI opportunities\"," +
                                "\"idempotencyKey\":\"idem-12345678\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(10))
                .andExpect(jsonPath("$.data.messageId").value(20))
                .andExpect(jsonPath("$.data.runId").value(30));
    }

    @Test
    void authenticatedAtomicSessionStartIsAccepted() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);

        mockMvc.perform(post("/api/ai/research/sessions/start")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profile":{"regionId":1,"industry":"AI"},
                                  "content":"Research Hubei AI opportunities",
                                  "idempotencyKey":"idem-start-12345"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void authenticatedSessionStartBindsTheExplicitTaskContext() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);
        var context = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"version":"phase3-task-v1","taskType":"case_comparison",
                 "caseIds":[101,102],"comparisonDimensions":["businessModel","outcome"]}
                """);
        when(researchService.start(any(), any())).thenReturn(new AgentResearchStartReceipt(
                null, 21L, 31L, "received", "case_comparison", "context-hash", context));

        mockMvc.perform(post("/api/ai/research/sessions/start")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content":"Compare the verified cases",
                                  "idempotencyKey":"idem-context-12345",
                                  "requestedIntent":"case_comparison",
                                  "taskContext":{
                                    "version":"phase3-task-v1",
                                    "taskType":"case_comparison",
                                    "caseIds":[101,102],
                                    "comparisonDimensions":["businessModel","outcome"]
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskType").value("case_comparison"))
                .andExpect(jsonPath("$.data.taskContextHash").value("context-hash"))
                .andExpect(jsonPath("$.data.taskContext.taskType").value("case_comparison"));

        ArgumentCaptor<AgentSessionStartDTO> request = ArgumentCaptor.forClass(AgentSessionStartDTO.class);
        verify(researchService).start(any(), request.capture());
        assertEquals("case_comparison", request.getValue().getTaskContext().path("taskType").asText());
    }

    @Test
    void invalidRequestedIntentUsesTheExistingBadRequestContract() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);

        mockMvc.perform(post("/api/ai/research/sessions/start")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profile":{"regionId":1,"industry":"AI"},
                                  "content":"Compare two verified cases",
                                  "requestedIntent":"invented_intent",
                                  "idempotencyKey":"idem-start-invalid"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("requestedIntent")));

        verify(researchService, never()).start(any(), any());
    }

    @Test
    void compatibilityDeleteUsesTheExplicitArchiveLifecycle() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);

        mockMvc.perform(delete("/api/ai/research/sessions/10")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(historyService).archive(any(), org.mockito.ArgumentMatchers.eq(10L));
    }

    @Test
    void anonymousMessageSubmissionNeverReachesResearchService() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"))
                .when(userAuthService).getCurrentUser(null);

        mockMvc.perform(post("/api/ai/research/sessions/10/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Research question\",\"idempotencyKey\":\"idem-12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        verify(researchService, never()).submit(any(), anyLong(), any());
    }

    @Test
    void disabledUserTokenNeverReachesAgentResearchService() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效"))
                .when(userAuthService).getCurrentUser("disabled-token");

        mockMvc.perform(post("/api/ai/research/sessions/10/messages")
                        .header("Authorization", "Bearer disabled-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Research question\",\"idempotencyKey\":\"idem-12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        verify(researchService, never()).submit(any(), anyLong(), any());
    }

    @Test
    void authenticatedUserCanReadThePaginatedHistoryContract() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);
        AgentSessionVO session = new AgentSessionVO(
                10L, "湖北人工智能创业机会", "auto", "active", null,
                true, null, null, null, null, null, null, null);
        when(historyService.history(any(), any(), any(), any(), anyInt()))
                .thenReturn(new AgentSessionHistoryPageVO(java.util.List.of(session), "next", true));

        mockMvc.perform(get("/api/ai/research/sessions/history")
                        .header("Authorization", "Bearer valid-token")
                        .param("scope", "active")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sessionId").value(10))
                .andExpect(jsonPath("$.data.items[0].title").value("湖北人工智能创业机会"))
                .andExpect(jsonPath("$.data.nextCursor").value("next"))
                .andExpect(jsonPath("$.data.hasMore").value(true));
    }

    @Test
    void staleHistoryCursorReturnsAControlledDiagnosticInsteadOfAnInternalError() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);
        when(historyService.history(any(), any(), any(), any(), anyInt()))
                .thenThrow(new AgentHistoryCursorStaleException());

        mockMvc.perform(get("/api/ai/research/sessions/history")
                        .header("Authorization", "Bearer valid-token")
                        .param("scope", "active")
                        .param("cursor", "signed-stale-cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.data.diagnosticCode").value("HISTORY_CURSOR_STALE"));
    }

    @Test
    void anonymousHistoryRequestNeverReachesHistoryService() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"))
                .when(userAuthService).getCurrentUser(null);

        mockMvc.perform(get("/api/ai/research/sessions/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        verify(historyService, never()).history(any(), any(), any(), any(), anyInt());
    }

    @Test
    void authenticatedUserCanRequestTheSanitizedRunEvidenceContract() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);

        mockMvc.perform(get("/api/ai/research/runs/30/evidence")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void anonymousRunEvidenceRequestNeverReachesTheQueryService() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"))
                .when(userAuthService).getCurrentUser(null);

        mockMvc.perform(get("/api/ai/research/runs/30/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void authenticatedOwnerCanReadBranchMaterialWithoutCreatingANewSession() throws Exception {
        UserLoginVO user = new UserLoginVO();
        user.setUserId(42L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        when(userAuthService.getCurrentUser("valid-token")).thenReturn(user);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        when(branchService.material(any(), org.mockito.ArgumentMatchers.eq(30L))).thenReturn(
                new AgentResearchBranchMaterialVO(
                        10L, 30L, "case_comparison",
                        mapper.readTree("{\"version\":\"phase3-task-v1\",\"taskType\":\"case_comparison\"}"),
                        "phase3-task-v1", "frozen-hash", "可见研究摘要",
                        mapper.readTree("[{\"sourceId\":8,\"claim\":\"已核验事实\"}]"), "evidence-v7"
                ));

        mockMvc.perform(get("/api/ai/research/runs/30/branch-material")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceSessionId").value(10))
                .andExpect(jsonPath("$.data.sourceRunId").value(30))
                .andExpect(jsonPath("$.data.taskContext.taskType").value("case_comparison"))
                .andExpect(jsonPath("$.data.resultSummary").value("可见研究摘要"))
                .andExpect(jsonPath("$.data.citations[0].sourceId").value(8));

        verify(branchService).material(any(), org.mockito.ArgumentMatchers.eq(30L));
        verify(researchService, never()).start(any(), any());
    }

    @Test
    void anonymousBranchMaterialRequestNeverReachesBranchService() throws Exception {
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"))
                .when(userAuthService).getCurrentUser(null);

        mockMvc.perform(get("/api/ai/research/runs/30/branch-material"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        verify(branchService, never()).material(any(), anyLong());
    }

    @Configuration
    @EnableWebMvc
    @Import({
            AiWebMvcConfig.class,
            SecurityConfig.class,
            UserAuthInterceptor.class,
            AgentResearchController.class,
            GlobalExceptionHandler.class
    })
    static class TestWebConfig {
        @Bean UserAuthService userAuthService() { return mock(UserAuthService.class); }
        @Bean AgentResearchService agentResearchService() { return mock(AgentResearchService.class); }
        @Bean AgentResearchBranchService agentResearchBranchService() { return mock(AgentResearchBranchService.class); }
        @Bean AgentResearchQueryService agentResearchQueryService() { return mock(AgentResearchQueryService.class); }
        @Bean AgentSessionHistoryService agentSessionHistoryService() { return mock(AgentSessionHistoryService.class); }
        @Bean AgentRunEvidenceService agentRunEvidenceService() { return mock(AgentRunEvidenceService.class); }
    }
}
