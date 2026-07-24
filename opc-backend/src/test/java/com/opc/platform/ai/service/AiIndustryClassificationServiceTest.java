package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiRuntimeSnapshot;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.casetag.mapper.CaseTagMapper;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import com.opc.platform.tag.service.IndustryTagService;
import com.opc.platform.tag.vo.IndustryResolution;
import com.opc.platform.tagalias.mapper.TagAliasMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiIndustryClassificationServiceTest {

    private AiRuntimeSettings settings;

    private final TagMapper tagMapper = mock(TagMapper.class);
    private final TagAliasMapper aliasMapper = mock(TagAliasMapper.class);
    private final CaseTagMapper caseTagMapper = mock(CaseTagMapper.class);
    private final PolicyTagMapper policyTagMapper = mock(PolicyTagMapper.class);
    private final AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final AiRuntimeSettingsProvider settingsProvider = mock(AiRuntimeSettingsProvider.class);

    private AiIndustryClassificationService service;

    @BeforeEach
    void setUp() {
        IndustryTagService industryTags = new IndustryTagService(
                tagMapper, aliasMapper, caseTagMapper, policyTagMapper
        );
        when(tagMapper.selectList(any())).thenReturn(List.of(
                industry(703L, "人工智能应用"),
                industry(517L, "智能零售")
        ));
        when(aliasMapper.selectList(any())).thenReturn(List.of());
        when(caseTagMapper.selectList(any())).thenReturn(List.of());
        when(policyTagMapper.selectList(any())).thenReturn(List.of());
        when(tagMapper.selectById(703L)).thenReturn(industry(703L, "人工智能应用"));
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        settings = new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "configured-model",
                "test-key", 0.2, 300, Duration.ofSeconds(20), 0, true
        );
        when(settingsProvider.snapshot()).thenAnswer(invocation -> new AiRuntimeSnapshot(settings, 10_000L));
        when(aiClient.descriptor(any(AiRuntimeSettings.class))).thenAnswer(invocation -> {
            AiRuntimeSettings runtime = invocation.getArgument(0);
            return new AiProviderDescriptor(runtime.provider(), runtime.model(), runtime.enabled());
        });
        when(aiClient.generate(any(), any(AiRuntimeSettings.class))).thenReturn(new AiProviderResponse(
                "{\"tagId\":703,\"confidence\":0.55}", 40, 10, 50, 100, "classify-1"
        ));
        when(runMapper.reserve(any(AiAnalysisRun.class), anyLong(), anyInt())).thenAnswer(invocation -> {
            AiAnalysisRun run = invocation.getArgument(0);
            run.setId(88L);
            return 1;
        });
        when(runMapper.settle(anyLong(), any(), any(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any()))
                .thenReturn(1);
        service = new AiIndustryClassificationService(
                industryTags,
                new AiTaskExecutionService(runMapper, aiClient, settingsProvider),
                settingsProvider,
                new ObjectMapper()
        );
    }

    @Test
    void paidClassificationUsesTaskExecutionAndCachesNormalizedText() {
        AuthenticatedUser user = new AuthenticatedUser(42L, "member", "member@example.com");

        IndustryResolution first = service.classify(user, " 农业 + 智能决策 ");
        IndustryResolution second = service.classify(user, "农业＋智能决策");

        assertEquals(703L, first.tagId());
        assertEquals("ai", first.method());
        assertTrue(first.requiresConfirmation());
        assertEquals(first, second);
        ArgumentCaptor<AiAnalysisRun> run = ArgumentCaptor.forClass(AiAnalysisRun.class);
        verify(runMapper).reserve(run.capture(), anyLong(), anyInt());
        assertEquals("industry_classification", run.getValue().getTaskType());
        verify(aiClient, times(1)).generate(any(), eq(settings));
    }

    @Test
    void cachedClassificationIsInvalidatedWhenTheConfiguredModelChanges() {
        AuthenticatedUser user = new AuthenticatedUser(42L, "member", "member@example.com");

        service.classify(user, "农业 + 智能决策");
        settings = new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "new-model",
                "test-key", 0.2, 300, Duration.ofSeconds(20), 0, true
        );
        service.classify(user, "农业 + 智能决策");

        verify(aiClient, times(2)).generate(any(), any(AiRuntimeSettings.class));
    }

    private Tag industry(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setTagType("common");
        tag.setIsIndustry(true);
        tag.setSortOrder(0);
        return tag;
    }
}
