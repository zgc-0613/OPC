package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentResearchQueryServiceTest {

    private final AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");

    @Test
    void sessionMessagesExposeOnlyTheFrozenStructuredResultSurface() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AiAgentToolCallMapper tools = mock(AiAgentToolCallMapper.class);
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentProfilePolicy profiles = mock(AgentProfilePolicy.class);
        AgentRunFeedbackService feedback = mock(AgentRunFeedbackService.class);
        AiAgentSession session = new AiAgentSession();
        session.setId(20L);
        session.setUserId(42L);
        session.setStatus("active");
        session.setTitle("Research");
        session.setTitleMode("auto");
        AiAgentMessage finalMessage = new AiAgentMessage();
        finalMessage.setId(31L);
        finalMessage.setSessionId(20L);
        finalMessage.setRunId(30L);
        finalMessage.setRole("assistant");
        finalMessage.setStatus("completed");
        finalMessage.setContent("Legacy visible answer");
        finalMessage.setSequenceNo(2);
        finalMessage.setCitationsJson("[]");
        finalMessage.setStructuredResultJson("{\"schemaVersion\":\"phase3-structured-result-v1\",\"directAnswer\":\"Frozen answer\"}");
        finalMessage.setAnalyticsSnapshotJson("{\"analyticsSnapshotId\":17,\"metricId\":\"overview.verified_cases\",\"dataVersion\":\"analytics-v1:current\"}");

        when(sessions.requireOwned(owner, 20L)).thenReturn(session);
        when(messages.selectMessagePage(20L, null, 51)).thenReturn(List.of(finalMessage));
        AgentResearchQueryService service = new AgentResearchQueryService(
                sessions, lifecycle, messages, tools, runs, new ObjectMapper(), profiles, feedback
        );

        var detail = service.sessionDetail(owner, 20L);

        assertEquals("Frozen answer", detail.messages().get(0).structuredResult().path("directAnswer").asText());
        assertEquals(17L, detail.messages().get(0).analyticsSnapshot().path("analyticsSnapshotId").asLong());
    }

    @Test
    void ownedRunPublishesTheServerComputedFeedbackEligibility() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AiAgentToolCallMapper tools = mock(AiAgentToolCallMapper.class);
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentProfilePolicy profiles = mock(AgentProfilePolicy.class);
        AgentRunFeedbackService feedback = mock(AgentRunFeedbackService.class);
        AiAnalysisRun run = completedRun();
        run.setResultJson("{\"structuredResult\":{},\"analyticsSnapshot\":{\"analyticsSnapshotId\":17,\"metricId\":\"overview.verified_cases\",\"dataVersion\":\"analytics-v1:current\"}}");
        AiAgentMessage finalMessage = new AiAgentMessage();
        finalMessage.setRunId(30L);
        AiAgentSession session = frozenSession("case_analysis");

        when(runs.selectOwnedAgentRun(30L, 42L)).thenReturn(run);
        when(sessions.requireOwned(owner, 20L)).thenReturn(session);
        when(messages.selectFinalByRun(30L)).thenReturn(finalMessage);
        when(feedback.feedbackEligible(run, finalMessage)).thenReturn(true);
        AgentResearchQueryService service = new AgentResearchQueryService(
                sessions, lifecycle, messages, tools, runs, new ObjectMapper(), profiles, feedback
        );

        var status = service.run(owner, 30L);
        assertTrue(status.feedbackEligible());
        assertEquals("case_analysis", status.taskType());
        assertEquals("session-context-hash", status.taskContextHash());
        assertEquals(17L, status.analyticsSnapshot().path("analyticsSnapshotId").asLong());
        verify(feedback).feedbackEligible(run, finalMessage);
    }

    @Test
    void ownedRunPublishesASafeTaskPlanWithoutInternalPlanningData() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AiAgentToolCallMapper tools = mock(AiAgentToolCallMapper.class);
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentProfilePolicy profiles = mock(AgentProfilePolicy.class);
        AgentRunFeedbackService feedback = mock(AgentRunFeedbackService.class);
        AiAnalysisRun run = completedRun();
        run.setRequestedIntent("general_research");

        when(runs.selectOwnedAgentRun(30L, 42L)).thenReturn(run);
        when(sessions.requireOwned(owner, 20L)).thenReturn(frozenSession("policy_lookup"));
        AgentResearchQueryService service = new AgentResearchQueryService(
                sessions, lifecycle, messages, tools, runs, new ObjectMapper(), profiles, feedback
        );

        assertEquals(List.of(
                "理解研究目标与适用条件",
                "检索匹配政策",
                "核验政策来源与时效",
                "整理适配性、限制与申请路径"
        ), service.run(owner, 30L).researchPlan());
    }

    @Test
    void ownedRunPublishesItsServerDeadlineWithoutLeaseMetadata() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AiAgentToolCallMapper tools = mock(AiAgentToolCallMapper.class);
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentProfilePolicy profiles = mock(AgentProfilePolicy.class);
        AgentRunFeedbackService feedback = mock(AgentRunFeedbackService.class);
        AiAnalysisRun run = completedRun();
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 2, 10, 5);
        run.setDeadlineAt(deadline);

        when(runs.selectOwnedAgentRun(30L, 42L)).thenReturn(run);
        when(sessions.requireOwned(owner, 20L)).thenReturn(frozenSession("general_research"));
        AgentResearchQueryService service = new AgentResearchQueryService(
                sessions, lifecycle, messages, tools, runs, new ObjectMapper(), profiles, feedback
        );

        assertEquals(deadline, service.run(owner, 30L).deadlineAt());
    }

    private AiAnalysisRun completedRun() {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(30L);
        run.setUserId(42L);
        run.setSessionId(20L);
        run.setStatus("completed");
        run.setTaskType("agent_research");
        run.setResultJson("{}");
        return run;
    }

    private AiAgentSession frozenSession(String taskType) {
        AiAgentSession session = new AiAgentSession();
        session.setId(20L);
        session.setUserId(42L);
        session.setStatus("active");
        session.setTaskContextVersion("phase3-task-v1");
        session.setTaskContextHash("session-context-hash");
        session.setTaskContextJson("{\"version\":\"phase3-task-v1\",\"taskType\":\"" + taskType
                + "\",\"caseIds\":[],\"comparisonDimensions\":[]}");
        return session;
    }
}
