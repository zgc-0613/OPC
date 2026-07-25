package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void lateProviderResultCannotOverwriteCancelledRun() {
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
            AiAnalysisRun run = invocation.getArgument(0);
            run.setId(91L);
            return 1;
        });
        when(aiClient.generate(any(AiProviderRequest.class), any(AiRuntimeSettings.class)))
                .thenReturn(new AiProviderResponse("{}", 10, 5, 15, 20, "req-late", "stop"));
        when(runMapper.recordAgentUsage(anyLong(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any()))
                .thenReturn(0);
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(runMapper, aiClient, settingsProvider);
        AgentRuntimeConfig config = new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan"
        );
        AgentRunLease lease = lifecycle.begin(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), 10L, 20L, "idem-12345678", config
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> lifecycle.invoke(lease, new AiProviderRequest("agent", "v1", "system", "user", "{}"))
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
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
