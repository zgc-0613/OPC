package com.opc.platform.ai.service;

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
                any(), any(), anyInt(), anyInt(), any(), any());
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
}
