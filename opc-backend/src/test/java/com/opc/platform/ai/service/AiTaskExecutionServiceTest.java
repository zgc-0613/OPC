package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTaskExecutionServiceTest {

    private final AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final AiRuntimeSettingsProvider settingsProvider = mock(AiRuntimeSettingsProvider.class);

    private AiTaskExecutionService service;

    @BeforeEach
    void setUp() {
        service = new AiTaskExecutionService(runMapper, aiClient, settingsProvider);
        when(settingsProvider.current()).thenReturn(new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "model",
                "test-secret", 0.2, 1200, Duration.ofSeconds(20), 1, true
        ));
        when(settingsProvider.dailyTokenQuota()).thenReturn(10_000L);
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "model", true));
        when(aiClient.generate(any())).thenReturn(new AiProviderResponse("{}", 1, 1, 2, 10, "request-ok"));
        when(runMapper.reserve(any(AiAnalysisRun.class), eq(10_000L), eq(1200))).thenAnswer(invocation -> {
            AiAnalysisRun run = invocation.getArgument(0);
            run.setId(9L);
            return 1;
        });
    }

    @Test
    void quotaReservationFailureStopsTheProviderBeforeItIsCalled() {
        when(runMapper.reserve(any(AiAnalysisRun.class), eq(10_000L), eq(1200))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.execute(
                task(), new AiProviderRequest("test", "v1", "system", "input", "{}"), execution -> "unused"
        ));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.getErrorCode());
        verify(aiClient, never()).generate(any());
    }

    @Test
    void parseFailureAfterProviderResponseRecordsActualConsumedTokens() {
        AiProviderResponse response = new AiProviderResponse("not json", 20, 30, 50, 88, "request-1");
        when(aiClient.generate(any())).thenReturn(response);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.execute(
                task(), new AiProviderRequest("test", "v1", "system", "input", "{}"), execution -> {
                    throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "invalid json");
                }
        ));

        assertEquals(ErrorCode.UPSTREAM_ERROR, exception.getErrorCode());
        verify(runMapper).settle(eq(9L), eq("failed"), eq("UPSTREAM_ERROR"), eq(20), eq(30), eq(50), eq(88L), eq("request-1"), eq(null));
    }

    @Test
    void staleRunningTasksAreFailedBeforeANewReservationIsMade() {
        service.execute(task(), new AiProviderRequest("test", "v1", "system", "input", "{}"), execution -> "ok");

        verify(runMapper).failExpiredRunning(any(LocalDateTime.class));
        verify(runMapper).reserve(any(AiAnalysisRun.class), eq(10_000L), eq(1200));
    }

    private AiTaskExecutionService.Task task() {
        return new AiTaskExecutionService.Task(
                new AuthenticatedUser(42L, "member", "member@example.com"),
                "entrepreneurship_advice", null, "test-v1", "evidence-hash"
        );
    }
}
