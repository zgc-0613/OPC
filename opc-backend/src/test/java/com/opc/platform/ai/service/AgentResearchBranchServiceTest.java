package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentResearchBranchServiceTest {

    private final AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");

    @Test
    void ownedCompletedRunReturnsOnlyFrozenUserVisibleBranchMaterial() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AiAnalysisRun run = completedRun();
        AiAgentMessage finalMessage = finalMessage();
        AgentSessionService sessions = mock(AgentSessionService.class);
        when(runs.selectOwnedAgentRun(30L, 42L)).thenReturn(run);
        when(messages.selectFinalByRun(30L)).thenReturn(finalMessage);
        when(sessions.requireOwned(owner, 20L)).thenReturn(frozenSession());
        AgentResearchBranchService service = new AgentResearchBranchService(runs, messages, sessions, new ObjectMapper());

        var material = service.material(owner, 30L);

        assertEquals(20L, material.sourceSessionId());
        assertEquals(30L, material.sourceRunId());
        assertEquals("case_comparison", material.requestedIntent());
        assertEquals("case_comparison", material.taskContext().path("taskType").asText());
        assertEquals("phase3-task-v1", material.taskContextVersion());
        assertEquals("frozen-hash", material.taskContextHash());
        assertEquals("两个已核验案例采用不同的获客路径。", material.resultSummary());
        assertEquals("evidence-v7", material.evidenceVersion());
        assertEquals(8L, material.citations().path(0).path("sourceId").asLong());
        assertFalse(material.citations().toString().contains("providerRequestId"));
    }

    @Test
    void foreignRunIsIndistinguishableFromAMissingRun() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentResearchBranchService service = new AgentResearchBranchService(
                runs, mock(AiAgentMessageMapper.class), mock(AgentSessionService.class), new ObjectMapper());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.material(owner, 30L));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void runningRunCannotBeUsedAsCompletedBranchMaterial() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAnalysisRun run = completedRun();
        run.setStatus("running");
        when(runs.selectOwnedAgentRun(30L, 42L)).thenReturn(run);
        AgentResearchBranchService service = new AgentResearchBranchService(
                runs, mock(AiAgentMessageMapper.class), mock(AgentSessionService.class), new ObjectMapper());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.material(owner, 30L));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }

    @Test
    void malformedResultJsonFallsBackToTheCompletedAssistantMessage() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AiAnalysisRun run = completedRun();
        run.setResultJson("{not-json");
        AiAgentMessage finalMessage = finalMessage();
        finalMessage.setContent("User-visible fallback summary");
        AgentSessionService sessions = mock(AgentSessionService.class);
        when(runs.selectOwnedAgentRun(30L, 42L)).thenReturn(run);
        when(messages.selectFinalByRun(30L)).thenReturn(finalMessage);
        when(sessions.requireOwned(owner, 20L)).thenReturn(frozenSession());
        AgentResearchBranchService service = new AgentResearchBranchService(runs, messages, sessions, new ObjectMapper());

        var material = service.material(owner, 30L);

        assertEquals("User-visible fallback summary", material.resultSummary());
        assertEquals(null, material.evidenceVersion());
    }

    private AiAnalysisRun completedRun() {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(30L);
        run.setUserId(42L);
        run.setSessionId(20L);
        run.setStatus("completed");
        run.setRequestedIntent("case_comparison");
        run.setResultJson("{\"providerRequestId\":\"must-not-leak\",\"structuredResult\":{\"directAnswer\":\"两个已核验案例采用不同的获客路径。\",\"evidenceVersion\":\"evidence-v7\"}}");
        return run;
    }

    private AiAgentSession frozenSession() {
        AiAgentSession session = new AiAgentSession();
        session.setId(20L);
        session.setUserId(42L);
        session.setStatus("active");
        session.setTaskContextVersion("phase3-task-v1");
        session.setTaskContextHash("frozen-hash");
        session.setTaskContextJson("{\"version\":\"phase3-task-v1\",\"taskType\":\"case_comparison\",\"caseIds\":[101,102],\"comparisonDimensions\":[\"businessModel\"]}");
        return session;
    }

    private AiAgentMessage finalMessage() {
        AiAgentMessage message = new AiAgentMessage();
        message.setId(50L);
        message.setRunId(30L);
        message.setRole("assistant");
        message.setStatus("completed");
        message.setContent("用户可见研究结果");
        message.setCitationsJson("[{\"sourceId\":8,\"claim\":\"案例一采用渠道合作\"}]");
        return message;
    }
}
