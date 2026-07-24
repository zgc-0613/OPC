package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiRuntimeSnapshot;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTaskExecutionServiceTest {

    private AiRuntimeSettings settings;

    private final AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final AiRuntimeSettingsProvider settingsProvider = mock(AiRuntimeSettingsProvider.class);

    private AiTaskExecutionService service;

    @BeforeEach
    void setUp() {
        service = new AiTaskExecutionService(runMapper, aiClient, settingsProvider);
        settings = new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "model",
                "test-secret", 0.2, 1200, Duration.ofSeconds(20), 1, true
        );
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(settings, 10_000L));
        when(aiClient.descriptor(settings)).thenReturn(new AiProviderDescriptor("deepseek", "model", true));
        when(aiClient.generate(any(), eq(settings))).thenReturn(new AiProviderResponse("{}", 1, 1, 2, 10, "request-ok"));
        when(runMapper.reserve(any(AiAnalysisRun.class), eq(10_000L), anyInt())).thenAnswer(invocation -> {
            AiAnalysisRun run = invocation.getArgument(0);
            run.setId(9L);
            return 1;
        });
        when(runMapper.settle(any(), any(), any(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any()))
                .thenReturn(1);
    }

    @Test
    void quotaReservationFailureStopsTheProviderBeforeItIsCalled() {
        when(runMapper.reserve(any(AiAnalysisRun.class), eq(10_000L), anyInt())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.execute(
                task(), new AiProviderRequest("test", "v1", "system", "input", "{}"), execution -> "unused"
        ));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.getErrorCode());
        verify(aiClient, never()).generate(any(), any(AiRuntimeSettings.class));
    }

    @Test
    void parseFailureAfterProviderResponseRecordsActualConsumedTokens() {
        AiProviderResponse response = new AiProviderResponse("not json", 20, 30, 50, 88, "request-1");
        when(aiClient.generate(any(), eq(settings))).thenReturn(response);

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
        verify(runMapper).reserve(any(AiAnalysisRun.class), eq(10_000L), anyInt());
    }

    @Test
    void reservationIncludesConservativePromptEstimateForChineseEnglishAndMixedText() {
        service.execute(task(), request("这是一个面向湖北创业者的人工智能应用分析请求".repeat(20)), execution -> "ok");
        service.execute(task(), request("Evaluate a bootstrapped artificial intelligence venture in Hubei. ".repeat(20)), execution -> "ok");
        service.execute(task(), request("湖北 AI startup 预算 10-50 万元，validate demand. ".repeat(20)), execution -> "ok");

        ArgumentCaptor<Integer> reservations = ArgumentCaptor.forClass(Integer.class);
        verify(runMapper, org.mockito.Mockito.times(3)).reserve(any(AiAnalysisRun.class), eq(10_000L), reservations.capture());
        assertTrue(reservations.getAllValues().stream().allMatch(value -> value > 1200));
    }

    @Test
    void estimatorUsesAConservativeUpperBoundForUnsegmentedAsciiEmojiAndRareCjk() {
        String ascii = "aZ09_-".repeat(500);
        String emoji = "🧠🚀".repeat(300);
        String rareCjk = "𠮷野家".repeat(300);

        int asciiEstimate = ConservativeTokenEstimator.estimate(request(ascii));
        int emojiEstimate = ConservativeTokenEstimator.estimate(request(emoji));
        int rareCjkEstimate = ConservativeTokenEstimator.estimate(request(rareCjk));

        assertTrue(asciiEstimate >= ascii.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertTrue(emojiEstimate >= emoji.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertTrue(rareCjkEstimate >= rareCjk.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    void oneImmutableSnapshotDrivesReservationAuditAndProviderRequest() {
        AiRuntimeSettings changedSettings = new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "changed-model",
                "changed-secret", 0.8, 4000, Duration.ofSeconds(60), 3, true
        );
        when(settingsProvider.current()).thenReturn(changedSettings);
        when(settingsProvider.dailyTokenQuota()).thenReturn(1L);

        service.execute(task(), request("snapshot consistency"), execution -> "ok");

        ArgumentCaptor<AiAnalysisRun> run = ArgumentCaptor.forClass(AiAnalysisRun.class);
        verify(runMapper).reserve(run.capture(), eq(10_000L), anyInt());
        assertEquals("model", run.getValue().getModelId());
        assertEquals(1200, run.getValue().getCompletionTokens());
        verify(aiClient).descriptor(settings);
        verify(aiClient).generate(any(), eq(settings));
        verify(settingsProvider, times(1)).snapshot();
        verify(settingsProvider, never()).current();
        verify(settingsProvider, never()).dailyTokenQuota();
    }

    @Test
    void quotaBoundaryIncludesPromptBeforeProviderCall() {
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(settings, 1200L));
        when(runMapper.reserve(any(AiAnalysisRun.class), eq(1200L), anyInt())).thenAnswer(invocation -> {
            int requested = invocation.getArgument(2);
            return requested <= 1200 ? 1 : 0;
        });

        BusinessException exception = assertThrows(BusinessException.class, () -> service.execute(
                task(), request("长中文输入".repeat(100)), execution -> "unused"
        ));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.getErrorCode());
        verify(aiClient, never()).generate(any(), any(AiRuntimeSettings.class));
    }

    @Test
    void missingProviderUsageSettlesWithTheConservativeReservation() {
        when(aiClient.generate(any(), eq(settings))).thenReturn(new AiProviderResponse("{}"));

        service.execute(task(), request("mixed 中文 prompt for accounting"), execution -> "ok");

        ArgumentCaptor<Integer> reservation = ArgumentCaptor.forClass(Integer.class);
        verify(runMapper).reserve(any(AiAnalysisRun.class), eq(10_000L), reservation.capture());
        verify(runMapper).settle(
                eq(9L), eq("completed"), eq(null), anyInt(), anyInt(), eq(reservation.getValue()),
                eq(0L), eq(null), eq("{}")
        );
    }

    @Test
    void lateProviderResponseCannotCompleteATaskAlreadyMarkedTimedOut() {
        when(runMapper.settle(
                eq(9L), eq("completed"), eq(null), anyInt(), anyInt(), anyInt(),
                anyLong(), any(), eq("{}")
        )).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.execute(
                task(), request("response arrives after task timeout"), execution -> "must not be returned"
        ));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verify(runMapper).settle(
                eq(9L), eq("completed"), eq(null), anyInt(), anyInt(), anyInt(),
                anyLong(), any(), eq("{}")
        );
        verify(runMapper, never()).settle(
                eq(9L), eq("failed"), any(), anyInt(), anyInt(), anyInt(),
                anyLong(), any(), any()
        );
    }

    private AiProviderRequest request(String userPrompt) {
        return new AiProviderRequest("test", "v1", "system instructions", userPrompt, "{\"type\":\"object\"}");
    }

    private AiTaskExecutionService.Task task() {
        return new AiTaskExecutionService.Task(
                new AuthenticatedUser(42L, "member", "member@example.com"),
                "entrepreneurship_advice", null, "test-v1", "evidence-hash"
        );
    }
}
