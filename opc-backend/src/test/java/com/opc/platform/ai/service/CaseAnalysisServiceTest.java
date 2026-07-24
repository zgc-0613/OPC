package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.CaseAnalysisRequestDTO;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiRuntimeSnapshot;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseAnalysisServiceTest {

    private AiRuntimeSettings settings;

    private final CaseItemMapper caseItemMapper = mock(CaseItemMapper.class);
    private final SourceMapper sourceMapper = mock(SourceMapper.class);
    private final PolicyMapper policyMapper = mock(PolicyMapper.class);
    private final AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final AiRuntimeSettingsProvider settingsProvider = mock(AiRuntimeSettingsProvider.class);

    private CaseAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new CaseAnalysisService(
                caseItemMapper,
                sourceMapper,
                policyMapper,
                runMapper,
                new ObjectMapper(),
                new AiTaskExecutionService(runMapper, aiClient, settingsProvider)
        );
        settings = new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "configured-model",
                "test-key", 0.2, 1200, Duration.ofSeconds(20), 1, true
        );
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(settings, 100_000L));
        when(aiClient.descriptor(settings)).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(runMapper.sumCompletedTokensToday(42L)).thenReturn(0L);
        when(runMapper.countRunningForUser(42L)).thenReturn(0);
        when(runMapper.insert(any(AiAnalysisRun.class))).thenAnswer(invocation -> {
            AiAnalysisRun run = invocation.getArgument(0);
            run.setId(99L);
            return 1;
        });
        when(runMapper.reserve(any(AiAnalysisRun.class), anyLong(), anyInt())).thenAnswer(invocation -> {
            AiAnalysisRun run = invocation.getArgument(0);
            run.setId(99L);
            return 1;
        });
    }

    @Test
    void missingOrUnpublishedCaseIsRejected() {
        CaseItem draft = caseItem("draft", "verified");
        when(caseItemMapper.selectById(1L)).thenReturn(draft);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.analyze(user(), request())
        );

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        verify(aiClient, never()).generate(any(), any(AiRuntimeSettings.class));
    }

    @Test
    void legacyUnverifiedEvidenceReturnsExplicitInsufficiencyWithoutCallingModel() {
        when(caseItemMapper.selectById(1L)).thenReturn(caseItem("published", "legacy_unverified"));

        var response = service.analyze(user(), request());

        assertEquals("insufficient", response.getEvidenceStatus());
        assertEquals("证据不足", response.getSummary());
        assertTrue(response.getCitations().isEmpty());
        assertEquals(99L, response.getAnalysisId());
        verify(aiClient, never()).generate(any(), any(AiRuntimeSettings.class));
    }

    @Test
    void dailyTokenQuotaStopsProviderCall() {
        when(caseItemMapper.selectById(1L)).thenReturn(caseItem("published", "verified"));
        when(sourceMapper.selectById(8L)).thenReturn(source("published", "verified"));
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(runMapper.reserve(any(AiAnalysisRun.class), anyLong(), anyInt())).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.analyze(user(), request())
        );

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.getErrorCode());
        verify(aiClient, never()).generate(any(), any(AiRuntimeSettings.class));
    }

    @Test
    void zeroDailyTokenQuotaDisablesTheDailyLimit() {
        when(caseItemMapper.selectById(1L)).thenReturn(caseItem("published", "verified"));
        when(sourceMapper.selectById(8L)).thenReturn(source("published", "verified"));
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(settings, 0L));
        when(runMapper.sumCompletedTokensToday(42L)).thenReturn(Long.MAX_VALUE);
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(aiClient.generate(any(), eq(settings))).thenReturn(new AiProviderResponse(
                """
                {
                  "summary":"无限额分析",
                  "businessModel":"订阅服务",
                  "technicalAssessment":"技术路径可验证",
                  "opportunities":["需求明确"],
                  "risks":["样本有限"],
                  "recommendedActions":["补充访谈"],
                  "citations":[{"sourceId":8,"claim":"商业模式来自案例来源"}],
                  "confidence":0.76
                }
                """,
                120,
                80,
                200,
                550,
                "req-unlimited"
        ));

        var response = service.analyze(user(), request());

        assertEquals("无限额分析", response.getSummary());
        verify(aiClient).generate(any(), eq(settings));
    }

    @Test
    void verifiedEvidenceProducesStructuredResultAndValidatedCitation() {
        when(caseItemMapper.selectById(1L)).thenReturn(caseItem("published", "verified"));
        when(sourceMapper.selectById(8L)).thenReturn(source("published", "verified"));
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(aiClient.generate(any(), eq(settings))).thenReturn(new AiProviderResponse(
                """
                {
                  "summary":"案例摘要",
                  "businessModel":"订阅服务",
                  "technicalAssessment":"技术路径可验证",
                  "opportunities":["需求明确"],
                  "risks":["样本有限"],
                  "recommendedActions":["补充访谈"],
                  "citations":[{"sourceId":8,"claim":"商业模式来自案例来源"}],
                  "confidence":0.76
                }
                """,
                120,
                80,
                200,
                550,
                "req-1"
        ));

        var response = service.analyze(user(), request());

        assertEquals("sufficient", response.getEvidenceStatus());
        assertEquals("案例摘要", response.getSummary());
        assertEquals(1, response.getCitations().size());
        assertEquals("来源标题", response.getCitations().get(0).getTitle());
        assertEquals("https://source.example/8", response.getCitations().get(0).getUrl());
        assertEquals(200, response.getTokenUsage().getTotalTokens());
    }

    @Test
    void malformedProviderJsonBecomesControlledUpstreamError() {
        when(caseItemMapper.selectById(1L)).thenReturn(caseItem("published", "verified"));
        when(sourceMapper.selectById(8L)).thenReturn(source("published", "verified"));
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(aiClient.generate(any(), eq(settings))).thenReturn(new AiProviderResponse("not-json"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.analyze(user(), request())
        );

        assertEquals(ErrorCode.UPSTREAM_ERROR, exception.getErrorCode());
        assertEquals("AI 返回内容格式无效，请稍后重试", exception.getMessage());
    }

    private CaseAnalysisRequestDTO request() {
        CaseAnalysisRequestDTO dto = new CaseAnalysisRequestDTO();
        dto.setCaseId(1L);
        dto.setUserQuestion("这个案例的技术风险是什么？");
        return dto;
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(42L, "ACha_", "acha@example.com");
    }

    private CaseItem caseItem(String status, String evidenceStatus) {
        CaseItem item = new CaseItem();
        item.setId(1L);
        item.setTitle("测试案例");
        item.setRegionId(3L);
        item.setSourceId(8L);
        item.setSummary("数据库中的案例摘要");
        item.setBusinessModel("数据库中的商业模式");
        item.setAiTools("模型与数据工具");
        item.setOutcome("阶段性结果");
        item.setStatus(status);
        item.setAiEvidenceStatus(evidenceStatus);
        return item;
    }

    private Source source(String status, String evidenceStatus) {
        Source source = new Source();
        source.setId(8L);
        source.setTitle("来源标题");
        source.setUrl("https://source.example/8");
        source.setNotes("经人工核验的来源说明");
        source.setStatus(status);
        source.setAiEvidenceStatus(evidenceStatus);
        return source;
    }
}
