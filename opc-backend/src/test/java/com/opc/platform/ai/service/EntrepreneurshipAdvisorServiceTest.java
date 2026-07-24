package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.EntrepreneurshipAdviceRequestDTO;
import com.opc.platform.ai.dto.EntrepreneurshipReadinessRequestDTO;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiRuntimeSnapshot;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.casetag.entity.CaseTag;
import com.opc.platform.casetag.mapper.CaseTagMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policytag.entity.PolicyTag;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.tag.service.IndustryTagService;
import com.opc.platform.tag.vo.IndustryResolution;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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

class EntrepreneurshipAdvisorServiceTest {

    private AiRuntimeSettings settings;

    private final CaseItemMapper caseItemMapper = mock(CaseItemMapper.class);
    private final PolicyMapper policyMapper = mock(PolicyMapper.class);
    private final SourceMapper sourceMapper = mock(SourceMapper.class);
    private final RegionMapper regionMapper = mock(RegionMapper.class);
    private final AiAnalysisRunMapper runMapper = mock(AiAnalysisRunMapper.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final AiRuntimeSettingsProvider settingsProvider = mock(AiRuntimeSettingsProvider.class);
    private final CaseTagMapper caseTagMapper = mock(CaseTagMapper.class);
    private final PolicyTagMapper policyTagMapper = mock(PolicyTagMapper.class);
    private final IndustryTagService industryTagService = mock(IndustryTagService.class);

    private EntrepreneurshipAdvisorService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        EntrepreneurshipEvidenceService evidenceService = new EntrepreneurshipEvidenceService(
                caseItemMapper,
                policyMapper,
                sourceMapper,
                regionMapper,
                caseTagMapper,
                policyTagMapper,
                industryTagService,
                aiClient,
                objectMapper
        );
        service = new EntrepreneurshipAdvisorService(
                caseItemMapper,
                policyMapper,
                sourceMapper,
                regionMapper,
                runMapper,
                aiClient,
                settingsProvider,
                objectMapper,
                evidenceService,
                new AiTaskExecutionService(runMapper, aiClient, settingsProvider)
        );
        when(regionMapper.selectById(3L)).thenReturn(region());
        when(regionMapper.selectList(any())).thenReturn(List.of(region()));
        when(caseItemMapper.selectList(any())).thenReturn(List.of());
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(sourceMapper.selectBatchIds(any())).thenAnswer(invocation ->
                ((Collection<Long>) invocation.getArgument(0)).stream()
                        .map(sourceMapper::selectById)
                        .filter(Objects::nonNull)
                        .toList()
        );
        when(caseTagMapper.selectList(any())).thenReturn(List.of(caseTag(11L, 703L)));
        when(policyTagMapper.selectList(any())).thenReturn(List.of(policyTag(21L, 703L)));
        when(industryTagService.resolve(any(), any(), any(Boolean.class))).thenReturn(
                new IndustryResolution(703L, "人工智能应用", "common", "tag_name", 1.0, false)
        );
        when(industryTagService.relatedTagIds(703L)).thenReturn(List.of(703L));
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("disabled", "unconfigured", false));
        settings = new AiRuntimeSettings(
                "deepseek", "openai_compatible", "https://api.example.com/v1", "configured-model",
                "test-key", 0.2, 1200, Duration.ofSeconds(20), 1, true
        );
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(settings, 100_000L));
        when(aiClient.descriptor(settings)).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(runMapper.insert(any(AiAnalysisRun.class))).thenAnswer(invocation -> {
            AiAnalysisRun run = invocation.getArgument(0);
            run.setId(101L);
            return 1;
        });
        when(runMapper.reserve(any(AiAnalysisRun.class), anyLong(), anyInt())).thenAnswer(invocation -> {
            AiAnalysisRun run = invocation.getArgument(0);
            run.setId(101L);
            return 1;
        });
    }

    @Test
    void missingVerifiedLocalEvidenceReturnsInsufficiencyWithoutCallingModel() {
        var result = service.advise(user(), request());

        assertEquals(101L, result.getAnalysisId());
        assertEquals("insufficient", result.getEvidenceStatus());
        assertEquals("证据不足", result.getSummary());
        assertTrue(result.getMatchedCases().isEmpty());
        assertTrue(result.getMatchedPolicies().isEmpty());
        assertTrue(result.getCitations().isEmpty());
        verify(aiClient, never()).generate(any(), any(AiRuntimeSettings.class));
    }

    @Test
    void automaticReadinessUsesDeterministicIndustryResolutionOnly() {
        EntrepreneurshipReadinessRequestDTO readinessRequest = new EntrepreneurshipReadinessRequestDTO();
        readinessRequest.setRegionId(3L);
        readinessRequest.setIndustryTagId(703L);
        readinessRequest.setIndustry("人工智能应用");

        service.readiness(readinessRequest);

        verify(industryTagService).resolve(eq(703L), eq("人工智能应用"), eq(false));
        verify(aiClient, never()).generate(any(), any(AiRuntimeSettings.class));
    }

    @Test
    void configuredEvidenceWithDisabledProviderReturnsServiceUnavailable() {
        when(caseItemMapper.selectList(any())).thenReturn(List.of(caseItem()));
        when(policyMapper.selectList(any())).thenReturn(List.of(policy()));
        when(sourceMapper.selectById(8L)).thenReturn(source());
        when(sourceMapper.selectById(9L)).thenReturn(source(9L, "政策原文"));
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("disabled", "unconfigured", false));
        when(aiClient.descriptor(settings)).thenReturn(new AiProviderDescriptor("disabled", "unconfigured", false));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.advise(user(), request())
        );

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
        verify(aiClient, never()).generate(any(), any(AiRuntimeSettings.class));
    }

    @Test
    void hubeiAiProfileExcludesZeroRelevanceRetailCaseFromDatabaseMatches() {
        when(caseItemMapper.selectList(any())).thenReturn(List.of(unrelatedCase(), caseItem()));
        when(policyMapper.selectList(any())).thenReturn(List.of(policy()));
        when(sourceMapper.selectById(8L)).thenReturn(source(8L, "案例核验来源"));
        when(sourceMapper.selectById(9L)).thenReturn(source(9L, "政策原文"));
        when(sourceMapper.selectById(10L)).thenReturn(source(10L, "无关案例来源"));
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(settings, 100_000L));
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(aiClient.generate(any(), eq(settings))).thenReturn(new AiProviderResponse(
                """
                {
                  "summary":"先验证细分行业的付费需求",
                  "recommendedDirection":"以轻量订阅工具切入北京本地专业服务市场。",
                  "opportunities":["现有原型可用于快速访谈"],
                  "risks":["付费意愿尚未形成连续证据"],
                  "actionPlan":["访谈十名目标客户","完成三次付费试点"],
                  "citations":[
                    {"sourceId":8,"claim":"相似案例采用订阅服务验证需求"},
                    {"sourceId":9,"claim":"地方政策包含创业支持措施"}
                  ],
                  "confidence":0.78
                }
                """,
                160,
                110,
                270,
                820,
                "req-advisor-1"
        ));

        var result = service.advise(user(), request());

        assertEquals("partial", result.getEvidenceStatus());
        assertEquals("先验证细分行业的付费需求", result.getSummary());
        assertEquals(1, result.getMatchedCases().size());
        assertEquals(11L, result.getMatchedCases().get(0).getId());
        assertEquals("/cases/11", result.getMatchedCases().get(0).getDetailUrl());
        assertEquals(1, result.getMatchedPolicies().size());
        assertEquals("/policies/21", result.getMatchedPolicies().get(0).getDetailUrl());
        assertEquals(2, result.getCitations().size());
        assertEquals("政策原文", result.getCitations().get(1).getTitle());
        assertEquals(270, result.getTokenUsage().getTotalTokens());
        ArgumentCaptor<AiProviderRequest> providerRequest = ArgumentCaptor.forClass(AiProviderRequest.class);
        verify(aiClient).generate(providerRequest.capture(), eq(settings));
        assertTrue(providerRequest.getValue().systemPrompt().contains("证据有限"));
        assertTrue(providerRequest.getValue().userPrompt().contains("\"readinessStatus\":\"partial\""));
    }

    @Test
    void dailyTokenQuotaStopsAdvisorBeforeProviderCall() {
        when(caseItemMapper.selectList(any())).thenReturn(List.of(caseItem()));
        when(policyMapper.selectList(any())).thenReturn(List.of(policy()));
        when(sourceMapper.selectById(8L)).thenReturn(source());
        when(sourceMapper.selectById(9L)).thenReturn(source(9L, "政策原文"));
        when(settingsProvider.snapshot()).thenReturn(new AiRuntimeSnapshot(settings, 100L));
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(runMapper.reserve(any(AiAnalysisRun.class), anyLong(), anyInt())).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.advise(user(), request())
        );

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.getErrorCode());
        verify(aiClient, never()).generate(any(), any(AiRuntimeSettings.class));
    }

    @Test
    void citationOutsideAssignedEvidenceBecomesControlledUpstreamError() {
        when(caseItemMapper.selectList(any())).thenReturn(List.of(caseItem()));
        when(policyMapper.selectList(any())).thenReturn(List.of(policy()));
        when(sourceMapper.selectById(8L)).thenReturn(source());
        when(sourceMapper.selectById(9L)).thenReturn(source(9L, "政策原文"));
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(aiClient.generate(any(), eq(settings))).thenReturn(new AiProviderResponse(
                """
                {
                  "summary":"不可信引用测试",
                  "recommendedDirection":"先验证需求。",
                  "opportunities":[],
                  "risks":[],
                  "actionPlan":[],
                  "citations":[{"sourceId":999,"claim":"模型编造的来源"}],
                  "confidence":0.3
                }
                """
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.advise(user(), request())
        );

        assertEquals(ErrorCode.UPSTREAM_ERROR, exception.getErrorCode());
        assertEquals("AI 返回内容格式无效，请稍后重试", exception.getMessage());
    }

    private EntrepreneurshipAdviceRequestDTO request() {
        EntrepreneurshipAdviceRequestDTO request = new EntrepreneurshipAdviceRequestDTO();
        request.setVentureType("solo_company");
        request.setRegionId(3L);
        request.setIndustry("人工智能应用");
        request.setStage("validation");
        request.setBudgetRange("100k_500k");
        request.setGoal("验证首批付费客户");
        request.setExistingResources("已有产品原型和两名行业顾问");
        request.setUserQuestion("优先验证哪类客户？");
        return request;
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(42L, "ACha_", "acha@example.com");
    }

    private Region region() {
        Region region = new Region();
        region.setId(3L);
        region.setName("湖北省");
        return region;
    }

    private CaseItem caseItem() {
        CaseItem item = new CaseItem();
        item.setId(11L);
        item.setTitle("湖北独立开发者案例");
        item.setRegionId(3L);
        item.setSourceId(8L);
        item.setCategory("软件工具");
        item.setSummary("通过行业工具验证付费需求");
        item.setBusinessModel("订阅服务");
        item.setStatus("published");
        item.setAiEvidenceStatus("verified");
        return item;
    }

    private CaseItem unrelatedCase() {
        CaseItem item = new CaseItem();
        item.setId(12L);
        item.setTitle("湖北传统零售门店案例");
        item.setRegionId(3L);
        item.setSourceId(10L);
        item.setCategory("线下零售");
        item.setSummary("通过门店选址增加客流");
        item.setBusinessModel("商品销售");
        item.setStatus("published");
        item.setAiEvidenceStatus("verified");
        return item;
    }

    private Source source() {
        return source(8L, "案例核验来源");
    }

    private Source source(Long id, String title) {
        Source source = new Source();
        source.setId(id);
        source.setTitle(title);
        source.setUrl("https://source.example/" + id);
        source.setNotes("经人工核验的案例记录");
        source.setStatus("published");
        source.setAiEvidenceStatus("verified");
        return source;
    }

    private Policy policy() {
        Policy policy = new Policy();
        policy.setId(21L);
        policy.setTitle("湖北省创业支持政策");
        policy.setRegionId(3L);
        policy.setSourceId(9L);
        policy.setPolicyType("创业支持");
        policy.setSummary("面向初创企业提供服务支持");
        policy.setSupportMeasures("创业辅导与资源对接");
        policy.setStatus("published");
        policy.setAiEvidenceStatus("verified");
        return policy;
    }

    private CaseTag caseTag(Long caseId, Long tagId) {
        CaseTag relation = new CaseTag();
        relation.setCaseId(caseId);
        relation.setTagId(tagId);
        return relation;
    }

    private PolicyTag policyTag(Long policyId, Long tagId) {
        PolicyTag relation = new PolicyTag();
        relation.setPolicyId(policyId);
        relation.setTagId(tagId);
        return relation;
    }
}
