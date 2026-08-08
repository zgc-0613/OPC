package com.opc.platform.ai.service;

import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.mapper.AiAgentProviderCallMapper;
import com.opc.platform.ai.entity.AiAgentProviderCall;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.ai.provider.AiRuntimeSnapshot;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

class AgentRunLifecycleServiceTest {

    @Test
    void completePersistsOutcomeDiagnosticCodeForAdminObservation() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        when(runMapper.settleAgentCompleted(
                eq(96L), eq("completed"), eq(20), eq(10), eq(30), eq(50L), eq("request-1"),
                eq("length"), eq(2), eq(1), eq("{\"diagnosticCode\":\"FINAL_RESPONSE_TRUNCATED_FALLBACK\"}"),
                eq(AgentResearchContract.FINAL_RESPONSE_TRUNCATED_FALLBACK), any()))
                .thenReturn(1);

        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(96L);
        run.setStatus("running");
        AgentRunLease lease = new AgentRunLease(
                run, null, null,
                new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan"));
        AgentOrchestratorOutcome outcome = new AgentOrchestratorOutcome(
                "completed", "Bounded fallback", java.util.List.of(), 0D, 2, 1,
                20, 10, 30, 50L, "request-1", "length", null,
                AgentResearchContract.FINAL_RESPONSE_TRUNCATED_FALLBACK);
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, mock(AiClient.class), mock(AiRuntimeSettingsProvider.class));

        lifecycle.complete(lease, outcome,
                "{\"diagnosticCode\":\"FINAL_RESPONSE_TRUNCATED_FALLBACK\"}");

        verify(runMapper).settleAgentCompleted(
                eq(96L), eq("completed"), eq(20), eq(10), eq(30), eq(50L), eq("request-1"),
                eq("length"), eq(2), eq(1), eq("{\"diagnosticCode\":\"FINAL_RESPONSE_TRUNCATED_FALLBACK\"}"),
                eq(AgentResearchContract.FINAL_RESPONSE_TRUNCATED_FALLBACK), any());
    }

    @Test
    void resumeRenewsDatabaseLeaseForTheBoundedRuntimeWindow() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiClient aiClient = mock(AiClient.class);
        AiRuntimeSettingsProvider settingsProvider = mock(AiRuntimeSettingsProvider.class);
        AiRuntimeSettings runtime = new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "model", "secret",
                0.2, 1200, Duration.ofSeconds(20), 0, true);
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(runtime, 100_000L));
        when(aiClient.descriptor(runtime)).thenReturn(new AiProviderDescriptor("deepseek", "model", true));
        when(runMapper.renewAgentLease(anyLong(), any(), any(), any())).thenReturn(1);
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(94L);
        run.setStatus("running");
        run.setProvider("deepseek");
        run.setModelId("model");
        run.setLeaseOwner("worker-a");
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(runMapper, aiClient, settingsProvider);
        LocalDateTime before = LocalDateTime.now();

        lifecycle.resume(run, new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan"));

        ArgumentCaptor<LocalDateTime> leaseExpiry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(runMapper).renewAgentLease(eq(94L), eq("worker-a"), any(), leaseExpiry.capture());
        assertTrue(leaseExpiry.getValue().isAfter(before.plusSeconds(119)));
    }

    @Test
    void cancelledDispatchedRunReconcilesEstimatedUsageAfterProviderFailure() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiAgentProviderCallMapper providerCalls = mock(AiAgentProviderCallMapper.class);
        when(providerCalls.markDispatchedEstimated(eq(93L), any())).thenReturn(1);
        when(runMapper.settleAgentFailed(
                eq(93L), eq("failed"), any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(0);
        when(runMapper.reconcileAgentProviderUsage(eq(93L), any())).thenReturn(1);
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(93L);
        run.setStatus("cancelled");
        AgentRunLease lease = new AgentRunLease(
                run, null, null,
                new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan"));
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, mock(AiClient.class), mock(AiRuntimeSettingsProvider.class), providerCalls);

        lifecycle.fail(lease, "failed", ErrorCode.UPSTREAM_ERROR, "PROVIDER_TIMEOUT");

        verify(providerCalls).markDispatchedEstimated(eq(93L), any());
        verify(runMapper).reconcileAgentProviderUsage(eq(93L), any());
    }

    @Test
    void lateActualUsageReplacesEstimateIdempotently() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiAgentProviderCallMapper providerCalls = mock(AiAgentProviderCallMapper.class);
        AiAgentProviderCall estimated = new AiAgentProviderCall();
        estimated.setId(501L);
        estimated.setAnalysisRunId(91L);
        estimated.setSettlementStatus("settled_estimated");
        AiAgentProviderCall actual = new AiAgentProviderCall();
        actual.setId(501L);
        actual.setAnalysisRunId(91L);
        actual.setSettlementStatus("settled_actual");
        when(providerCalls.selectForUpdate(501L)).thenReturn(estimated, actual);
        when(providerCalls.replaceEstimateWithActual(
                anyLong(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any())).thenReturn(1);
        when(runMapper.reconcileAgentProviderUsage(eq(91L), any())).thenReturn(1);
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, mock(AiClient.class), mock(AiRuntimeSettingsProvider.class), providerCalls);
        AiProviderResponse response = new AiProviderResponse(
                "{}", 10, 5, 15, 20, null, "stop");

        boolean first = lifecycle.settleLateUsage(501L, response);
        boolean duplicate = lifecycle.settleLateUsage(501L, response);

        assertEquals(true, first);
        assertEquals(false, duplicate);
        verify(providerCalls).replaceEstimateWithActual(
                eq(501L), eq(10), eq(5), eq(15), eq(20L), eq("not_provided"), eq("stop"), any());
        verify(runMapper).reconcileAgentProviderUsage(eq(91L), any());
    }

    @Test
    void enqueuePersistsReceivedRunWithoutTreatingWakeupAsExecution() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiClient aiClient = mock(AiClient.class);
        AiRuntimeSettingsProvider settingsProvider = mock(AiRuntimeSettingsProvider.class);
        AiRuntimeSettings runtime = new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "model", "secret",
                0.2, 1200, Duration.ofSeconds(20), 0, true
        );
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(runtime, 100_000L));
        when(aiClient.descriptor(runtime)).thenReturn(new AiProviderDescriptor("deepseek", "model", true));
        when(runMapper.reserve(any(AiAnalysisRun.class), anyLong(), anyInt())).thenAnswer(invocation -> {
            invocation.<AiAnalysisRun>getArgument(0).setId(90L);
            return 1;
        });
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(runMapper, aiClient, settingsProvider);

        AiAnalysisRun run = lifecycle.enqueue(
                new AuthenticatedUser(42L, "owner", "owner@example.com"),
                10L,
                20L,
                "idem-enqueue-123",
                new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan")
        );

        assertEquals(90L, run.getId());
        assertEquals("received", run.getStatus());
        ArgumentCaptor<AiAnalysisRun> inserted = ArgumentCaptor.forClass(AiAnalysisRun.class);
        verify(runMapper).reserve(inserted.capture(), eq(100_000L), eq(8000));
        assertEquals("received", inserted.getValue().getStatus());
    }

    @Test
    void lateProviderUsageIsSettledWithoutOverwritingCancelledRun() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiAgentProviderCallMapper providerCalls = mock(AiAgentProviderCallMapper.class);
        AiClient aiClient = mock(AiClient.class);
        AiRuntimeSettingsProvider settingsProvider = mock(AiRuntimeSettingsProvider.class);
        AiRuntimeSettings runtime = new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "model", "secret",
                0.2, 1200, Duration.ofSeconds(20), 0, true
        );
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(runtime, 100_000L));
        when(aiClient.descriptor(runtime)).thenReturn(new AiProviderDescriptor("deepseek", "model", true));
        when(runMapper.reserve(any(AiAnalysisRun.class), anyLong(), anyInt())).thenAnswer(invocation -> {
            AiAnalysisRun run = invocation.getArgument(0);
            run.setId(91L);
            return 1;
        });
        when(aiClient.generate(any(AiProviderRequest.class), any(AiRuntimeSettings.class)))
                .thenReturn(new AiProviderResponse("{}", 10, 5, 15, 20, "req-late", "stop"));
        when(providerCalls.insert(any(AiAgentProviderCall.class))).thenAnswer(invocation -> {
            invocation.<AiAgentProviderCall>getArgument(0).setId(501L);
            return 1;
        });
        AiAgentProviderCall dispatched = new AiAgentProviderCall();
        dispatched.setId(501L);
        dispatched.setAnalysisRunId(91L);
        dispatched.setSettlementStatus("provider_dispatched");
        when(providerCalls.selectForUpdate(501L)).thenReturn(dispatched);
        when(runMapper.markAgentProviderDispatched(anyLong(), any())).thenReturn(1);
        when(providerCalls.settleActual(anyLong(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any()))
                .thenReturn(1);
        when(runMapper.settleAgentUsageActual(anyLong(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any()))
                .thenReturn(1);
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, aiClient, settingsProvider, providerCalls);
        AgentRuntimeConfig config = new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan"
        );
        AgentRunLease lease = lifecycle.begin(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), 10L, 20L, "idem-12345678", config
        );

        AiProviderResponse response = lifecycle.invoke(
                lease, new AiProviderRequest("agent", "v1", "system", "user", "{}"));

        assertEquals(15, response.totalTokens());
        verify(runMapper).settleAgentUsageActual(
                eq(91L), eq(10), eq(5), eq(15), eq(20L), eq("req-late"), eq("stop"), any());
        verify(runMapper, never()).settleAgentCompleted(anyLong(), any(), anyInt(), anyInt(), anyInt(), anyLong(),
                any(), any(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    void providerResponseReplacesConcurrentEstimateExactlyOnce() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiAgentProviderCallMapper providerCalls = mock(AiAgentProviderCallMapper.class);
        AiClient aiClient = mock(AiClient.class);
        AiRuntimeSettingsProvider settingsProvider = mock(AiRuntimeSettingsProvider.class);
        AiRuntimeSettings runtime = new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "model", "secret",
                0.2, 1200, Duration.ofSeconds(20), 0, true);
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(runtime, 100_000L));
        when(aiClient.descriptor(runtime)).thenReturn(new AiProviderDescriptor("deepseek", "model", true));
        when(runMapper.reserve(any(AiAnalysisRun.class), anyLong(), anyInt())).thenAnswer(invocation -> {
            invocation.<AiAnalysisRun>getArgument(0).setId(95L);
            return 1;
        });
        when(runMapper.markAgentProviderDispatched(eq(95L), any())).thenReturn(1);
        when(providerCalls.insert(any(AiAgentProviderCall.class))).thenAnswer(invocation -> {
            invocation.<AiAgentProviderCall>getArgument(0).setId(502L);
            return 1;
        });
        AiAgentProviderCall estimated = new AiAgentProviderCall();
        estimated.setId(502L);
        estimated.setAnalysisRunId(95L);
        estimated.setSettlementStatus("settled_estimated");
        when(providerCalls.selectForUpdate(502L)).thenReturn(estimated);
        when(providerCalls.replaceEstimateWithActual(
                anyLong(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any())).thenReturn(1);
        when(runMapper.reconcileAgentProviderUsage(eq(95L), any())).thenReturn(1);
        when(aiClient.generate(any(AiProviderRequest.class), eq(runtime)))
                .thenReturn(new AiProviderResponse("{}", 9, 4, 13, 30, "req-actual", "stop"));
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, aiClient, settingsProvider, providerCalls);
        AgentRunLease lease = lifecycle.begin(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), 10L, 20L, "idem-estimate-123",
                new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan"));

        AiProviderResponse response = lifecycle.invoke(
                lease, new AiProviderRequest("agent", "v2", "system", "user", "{}"));

        assertEquals(13, response.totalTokens());
        verify(providerCalls).replaceEstimateWithActual(
                eq(502L), eq(9), eq(4), eq(13), eq(30L), eq("req-actual"), eq("stop"), any());
        verify(runMapper).reconcileAgentProviderUsage(eq(95L), any());
        assertEquals(13, lease.totalTokens());
    }

    @Test
    void heartbeatRenewsTheCapturedLeaseGenerationWithoutCrossingTheRunDeadline() throws Exception {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiAnalysisRun run = fencedRun(97L, "worker-a", 7);
        run.setHeartbeatAt(LocalDateTime.now());
        run.setLeaseExpiresAt(run.getHeartbeatAt().plusNanos(300_000_000));
        run.setDeadlineAt(run.getHeartbeatAt().plusSeconds(2));
        AgentRunLease lease = new AgentRunLease(run, runtime(Duration.ofSeconds(20)), null, config());
        CountDownLatch renewed = new CountDownLatch(1);
        when(runMapper.renewAgentLeaseFenced(eq(97L), eq("worker-a"), eq(7), any(), any()))
                .thenAnswer(invocation -> {
                    renewed.countDown();
                    return 1;
                });
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                    runMapper, mock(AiClient.class), mock(AiRuntimeSettingsProvider.class), null, null, scheduler);
            try (AgentRunLifecycleService.LeaseHeartbeat ignored = lifecycle.startLeaseHeartbeat(lease)) {
                assertTrue(renewed.await(1, TimeUnit.SECONDS));
            }
            ArgumentCaptor<LocalDateTime> expiry = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(runMapper).renewAgentLeaseFenced(eq(97L), eq("worker-a"), eq(7), any(), expiry.capture());
            assertTrue(!expiry.getValue().isAfter(run.getDeadlineAt()));
            assertTrue(run.getHeartbeatAt().isBefore(run.getDeadlineAt()));
        } finally {
            scheduler.shutdownNow();
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void differentWorkerCannotRenewAnotherWorkersGeneration() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiAnalysisRun run = fencedRun(98L, "worker-b", 8);
        AgentRunLease wrongWorkerLease = new AgentRunLease(run, runtime(Duration.ofSeconds(20)), null, config());
        when(runMapper.renewAgentLeaseFenced(eq(98L), eq("worker-b"), eq(8), any(), any())).thenReturn(0);
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, mock(AiClient.class), mock(AiRuntimeSettingsProvider.class));

        assertEquals(false, lifecycle.renewLease(wrongWorkerLease));
        assertTrue(wrongWorkerLease.leaseLost());
        verify(runMapper).renewAgentLeaseFenced(eq(98L), eq("worker-b"), eq(8), any(), any());
    }

    @Test
    void staleGenerationCannotWriteUsageProgressOrTerminalResult() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiClient aiClient = mock(AiClient.class);
        AiAnalysisRun run = fencedRun(99L, "worker-a", 4);
        AgentRuntimeConfig config = config();
        AiRuntimeSettings runtime = runtime(Duration.ofSeconds(20));
        when(aiClient.generate(any(AiProviderRequest.class), any(AiRuntimeSettings.class)))
                .thenReturn(new AiProviderResponse("{}", 2, 1, 3, 1, "late", "stop"));
        when(runMapper.recordAgentUsageFenced(eq(99L), eq("worker-a"), eq(4),
                anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any())).thenReturn(0);
        when(runMapper.updateAgentStageFenced(eq(99L), eq("worker-a"), eq(4),
                any(), any(), anyInt(), anyInt(), any())).thenReturn(0);
        when(runMapper.settleAgentCompletedFenced(eq(99L), eq("worker-a"), eq(4), any(), anyInt(), anyInt(),
                anyInt(), anyLong(), any(), any(), anyInt(), anyInt(), any(), any(), any())).thenReturn(0);
        when(runMapper.settleAgentFailedFenced(eq(99L), eq("worker-a"), eq(4), any(), any(), any(), any(),
                anyInt(), anyInt(), any())).thenReturn(0);
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, aiClient, mock(AiRuntimeSettingsProvider.class));

        AgentOrchestratorException usageRejected = assertThrows(AgentOrchestratorException.class,
                () -> lifecycle.invoke(new AgentRunLease(run, runtime, null, config),
                        new AiProviderRequest("agent", "v1", "system", "user", "{}")));
        assertEquals("AGENT_LEASE_LOST", usageRejected.getDiagnosticCode());
        assertThrows(BusinessException.class, () -> lifecycle.updateStage(
                new AgentRunLease(run, runtime, null, config), "synthesizing", 4, 1));
        AgentOrchestratorOutcome outcome = new AgentOrchestratorOutcome(
                "completed", "late", java.util.List.of(), 0.0, 1, 0,
                0, 0, 0, 0L, null, "stop", null, null);
        assertThrows(BusinessException.class, () -> lifecycle.complete(
                new AgentRunLease(run, runtime, null, config), outcome, "{}"));
        lifecycle.fail(new AgentRunLease(run, runtime, null, config), "failed", ErrorCode.UPSTREAM_ERROR, "PROVIDER_TIMEOUT");

        verify(runMapper).recordAgentUsageFenced(eq(99L), eq("worker-a"), eq(4),
                anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any());
        verify(runMapper).updateAgentStageFenced(eq(99L), eq("worker-a"), eq(4),
                any(), any(), anyInt(), anyInt(), any());
        verify(runMapper).settleAgentCompletedFenced(eq(99L), eq("worker-a"), eq(4), any(), anyInt(), anyInt(),
                anyInt(), anyLong(), any(), any(), anyInt(), anyInt(), any(), any(), any());
        verify(runMapper).settleAgentFailedFenced(eq(99L), eq("worker-a"), eq(4), any(), any(), any(), any(),
                anyInt(), anyInt(), any());
    }

    @Test
    void providerTimeoutIsBoundedByRemainingRunDeadlineAndLateResponseIsRejected() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiClient aiClient = mock(AiClient.class);
        AiAnalysisRun run = fencedRun(100L, "worker-a", 9);
        run.setDeadlineAt(LocalDateTime.now().plusSeconds(10));
        AiRuntimeSettings runtime = runtime(Duration.ofSeconds(20));
        AgentRuntimeConfig config = config();
        when(aiClient.generate(any(AiProviderRequest.class), any(AiRuntimeSettings.class))).thenAnswer(invocation -> {
            run.setDeadlineAt(LocalDateTime.now().minusNanos(1_000_000));
            return new AiProviderResponse("ignored", 1, 1, 2, 1, "late", "stop");
        });
        when(runMapper.recordAgentUsageFenced(eq(100L), eq("worker-a"), eq(9),
                anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any())).thenReturn(0);
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, aiClient, mock(AiRuntimeSettingsProvider.class));

        AgentOrchestratorException timeout = assertThrows(AgentOrchestratorException.class,
                () -> lifecycle.invoke(new AgentRunLease(run, runtime, null, config),
                        new AiProviderRequest("agent", "v1", "system", "user", "{}")));

        assertEquals("AGENT_TIMEOUT", timeout.getDiagnosticCode());
        ArgumentCaptor<AiRuntimeSettings> bounded = ArgumentCaptor.forClass(AiRuntimeSettings.class);
        verify(aiClient).generate(any(AiProviderRequest.class), bounded.capture());
        assertTrue(bounded.getValue().timeout().compareTo(Duration.ofSeconds(3)) < 0);
        verify(runMapper).recordAgentUsageFenced(eq(100L), eq("worker-a"), eq(9),
                anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any());
    }

    @Test
    void elapsedDeadlinePreventsAnyNewProviderRequest() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiClient aiClient = mock(AiClient.class);
        AiAnalysisRun run = fencedRun(101L, "worker-a", 10);
        run.setDeadlineAt(LocalDateTime.now().minusSeconds(1));
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, aiClient, mock(AiRuntimeSettingsProvider.class));

        AgentOrchestratorException timeout = assertThrows(AgentOrchestratorException.class,
                () -> lifecycle.invoke(new AgentRunLease(run, runtime(Duration.ofSeconds(20)), null, config()),
                        new AiProviderRequest("agent", "v1", "system", "user", "{}")));

        assertEquals("AGENT_TIMEOUT", timeout.getDiagnosticCode());
        verify(aiClient, never()).generate(any(AiProviderRequest.class), any(AiRuntimeSettings.class));
        verify(runMapper, never()).recordAgentUsageFenced(
                anyLong(), any(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any());
    }

    @Test
    void nearDeadlineRejectsProviderDispatchWithControlledFallbackSignalAndKeepsLease() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiClient aiClient = mock(AiClient.class);
        AiAnalysisRun run = fencedRun(102L, "worker-a", 11);
        run.setDeadlineAt(LocalDateTime.now().plusSeconds(2));
        AgentRunLease lease = new AgentRunLease(run, runtime(Duration.ofSeconds(20)), null, config());
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                runMapper, aiClient, mock(AiRuntimeSettingsProvider.class));

        AgentOrchestratorException deadline = assertThrows(AgentOrchestratorException.class,
                () -> lifecycle.invoke(lease,
                        new AiProviderRequest("agent", "v1", "system", "user", "{}")));

        assertEquals("AGENT_DEADLINE_FALLBACK", deadline.getDiagnosticCode());
        assertTrue(!lease.leaseLost());
        verify(aiClient, never()).generate(any(AiProviderRequest.class), any(AiRuntimeSettings.class));
        verify(runMapper, never()).recordAgentUsageFenced(
                anyLong(), any(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any());
    }

    @Test
    void failedRunKeepsLastModelRoundAndToolCallCounts() {
        AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
        AiRuntimeSettingsProvider settingsProvider = mock(AiRuntimeSettingsProvider.class);
        AiClient aiClient = mock(AiClient.class);
        AiRuntimeSettings runtime = new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "model", "secret",
                0.2, 1200, Duration.ofSeconds(20), 0, true
        );
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(runtime, 100_000L));
        when(aiClient.descriptor(runtime)).thenReturn(new AiProviderDescriptor("deepseek", "model", true));
        when(runMapper.reserve(any(AiAnalysisRun.class), anyLong(), anyInt())).thenAnswer(invocation -> {
            AiAnalysisRun run = invocation.getArgument(0);
            run.setId(92L);
            return 1;
        });
        when(runMapper.updateAgentStage(anyLong(), any(), any(), anyInt(), anyInt(), any())).thenReturn(1);
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(runMapper, aiClient, settingsProvider);
        AgentRunLease lease = lifecycle.begin(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), 10L, 20L, "idem-counts-123",
                new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan")
        );

        lifecycle.updateStage(lease, "tool_running", 2, 1);
        lifecycle.fail(lease, "failed", ErrorCode.UPSTREAM_ERROR, "PROVIDER_5XX");

        verify(runMapper).settleAgentFailed(
                eq(92L), eq("failed"), any(), eq(ErrorCode.UPSTREAM_ERROR.name()), eq("PROVIDER_5XX"),
                eq(2), eq(1), any()
        );
    }

    private AgentRuntimeConfig config() {
        return new AgentRuntimeConfig(true, 4, 6, 8_000, 12, Duration.ofSeconds(120), "json_plan");
    }

    private AiRuntimeSettings runtime(Duration timeout) {
        return new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "model", "secret",
                0.2, 1200, timeout, 0, true);
    }

    private AiAnalysisRun fencedRun(Long id, String owner, int attempt) {
        LocalDateTime now = LocalDateTime.now();
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(id);
        run.setStatus("running");
        run.setLeaseOwner(owner);
        run.setExecutionAttempts(attempt);
        run.setHeartbeatAt(now);
        run.setLeaseExpiresAt(now.plusSeconds(30));
        run.setDeadlineAt(now.plusSeconds(60));
        return run;
    }
}
