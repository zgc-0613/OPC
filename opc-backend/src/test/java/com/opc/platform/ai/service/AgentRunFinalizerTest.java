package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AgentRunFinalizerTest {

    @Test
    void reclaimedRunWithTheSameOwnerCannotAppendAnAssistantMessageFromTheOldAttempt() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentSessionService sessions = mock(AgentSessionService.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AgentRunFinalizer finalizer = new AgentRunFinalizer(runs, sessions, lifecycle, objectMapper);
        AuthenticatedUser user = new AuthenticatedUser(42L, "owner", "owner@example.com");

        AiAgentSession session = new AiAgentSession();
        session.setId(10L);
        session.setStatus("active");
        session.setContentGeneration(0L);
        AiAnalysisRun firstAttempt = runningRun();
        firstAttempt.setExecutionAttempts(1);
        AiAnalysisRun reclaimed = runningRun();
        reclaimed.setExecutionAttempts(2);
        when(sessions.lockOwned(user, 10L)).thenReturn(session);
        when(runs.selectRunForUpdate(91L)).thenReturn(reclaimed);
        AgentRunLease staleLease = new AgentRunLease(firstAttempt, null, null, new AgentRuntimeConfig(
                true, 4, 6, 8_000, 12, Duration.ofSeconds(120), "json_plan"));
        AgentOrchestratorOutcome outcome = new AgentOrchestratorOutcome(
                "completed", "No late answer", List.of(), 0.0, 1, 0,
                0, 0, 0, 0L, null, "stop", objectMapper.createObjectNode(), null);

        assertThrows(RuntimeException.class, () -> finalizer.complete(staleLease, user, outcome, "[]"));

        verify(sessions, never()).appendMessage(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(lifecycle, never()).complete(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void persistsTheServerBoundAnalyticsSnapshotOutsideTheStrictStructuredResult() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentSessionService sessions = mock(AgentSessionService.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AgentRunFinalizer finalizer = new AgentRunFinalizer(runs, sessions, lifecycle, objectMapper);
        AuthenticatedUser user = new AuthenticatedUser(42L, "owner", "owner@example.com");

        AiAgentSession session = new AiAgentSession();
        session.setId(10L);
        session.setStatus("active");
        session.setContentGeneration(0L);
        AiAnalysisRun leaseRun = runningRun();
        AiAnalysisRun locked = runningRun();
        AiAgentMessage message = new AiAgentMessage();
        message.setId(501L);
        when(sessions.lockOwned(user, 10L)).thenReturn(session);
        when(runs.selectRunForUpdate(91L)).thenReturn(locked);
        when(sessions.appendMessage(user, 10L, "assistant", "Grounded answer", "completed", 91L, "[]"))
                .thenReturn(message);
        AgentRunLease lease = new AgentRunLease(leaseRun, null, null, new AgentRuntimeConfig(
                true, 4, 6, 8_000, 12, Duration.ofSeconds(120), "json_plan"));
        var structured = objectMapper.createObjectNode();
        structured.put("schemaVersion", "phase3-structured-result-v1");
        structured.putNull("dataVersion");
        AgentOrchestratorOutcome outcome = new AgentOrchestratorOutcome(
                "completed", "Grounded answer", List.of(), 0.8, 2, 1,
                20, 10, 30, 50L, "request-1", "length", structured,
                AgentResearchContract.FINAL_RESPONSE_TRUNCATED_FALLBACK);

        finalizer.complete(lease, user, outcome, "[]");

        ArgumentCaptor<String> resultJson = ArgumentCaptor.forClass(String.class);
        verify(lifecycle).complete(eq(lease), eq(outcome), resultJson.capture());
        var result = objectMapper.readTree(resultJson.getValue());
        assertEquals(AgentResearchContract.FINAL_RESPONSE_TRUNCATED_FALLBACK,
                result.path("diagnosticCode").asText());
        assertEquals("analytics-v1:current", result.path("structuredResult").path("dataVersion").asText());
        assertFalse(result.path("structuredResult").has("analyticsSnapshot"));
        var analytics = result.path("analyticsSnapshot");
        assertEquals(17L, analytics.path("analyticsSnapshotId").asLong());
        assertEquals("overview.verified_cases", analytics.path("metricId").asText());
        assertEquals("analytics-v1:current", analytics.path("dataVersion").asText());
        assertTrue(analytics.path("filters").isObject());
        assertEquals(3, analytics.path("snapshot").path("value").asInt());
    }

    private AiAnalysisRun runningRun() {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(91L);
        run.setSessionId(10L);
        run.setStatus("running");
        run.setLeaseOwner("worker-1");
        run.setSessionContentGeneration(0L);
        run.setAnalyticsSnapshotId(17L);
        run.setAnalyticsMetricId("overview.verified_cases");
        run.setAnalyticsDataVersion("analytics-v1:current");
        run.setAnalyticsFiltersJson("{}");
        run.setAnalyticsSnapshotJson("{\"metricId\":\"overview.verified_cases\","
                + "\"dataVersion\":\"analytics-v1:current\",\"value\":3}");
        return run;
    }
}
