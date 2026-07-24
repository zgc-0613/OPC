package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.EntrepreneurshipReadinessRequestDTO;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.casetag.entity.CaseTag;
import com.opc.platform.casetag.mapper.CaseTagMapper;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policytag.entity.PolicyTag;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.tag.service.IndustryTagService;
import com.opc.platform.tag.vo.IndustryResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntrepreneurshipEvidenceServiceTest {

    private final CaseItemMapper caseItemMapper = mock(CaseItemMapper.class);
    private final PolicyMapper policyMapper = mock(PolicyMapper.class);
    private final SourceMapper sourceMapper = mock(SourceMapper.class);
    private final RegionMapper regionMapper = mock(RegionMapper.class);
    private final CaseTagMapper caseTagMapper = mock(CaseTagMapper.class);
    private final PolicyTagMapper policyTagMapper = mock(PolicyTagMapper.class);
    private final IndustryTagService industryTagService = mock(IndustryTagService.class);
    private final AiClient aiClient = mock(AiClient.class);

    private EntrepreneurshipEvidenceService service;

    @BeforeEach
    void setUp() {
        service = new EntrepreneurshipEvidenceService(
                caseItemMapper,
                policyMapper,
                sourceMapper,
                regionMapper,
                caseTagMapper,
                policyTagMapper,
                industryTagService,
                aiClient,
                new ObjectMapper()
        );
        when(regionMapper.selectById(4201L)).thenReturn(city());
        when(regionMapper.selectList(any())).thenReturn(List.of(country(), province(), city(), crossRegion()));
        when(industryTagService.resolve(703L, "人工智能应用", true)).thenReturn(
                new IndustryResolution(703L, "人工智能应用", "common", "tag_id", 1.0, false)
        );
        when(industryTagService.relatedTagIds(703L)).thenReturn(List.of(703L, 541L));
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(caseItemMapper.selectList(any())).thenReturn(List.of());
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(caseTagMapper.selectList(any())).thenReturn(List.of());
        when(policyTagMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void filtersAllCandidatesBeforeLimitAndReturnsLayeredRegionCounts() {
        List<CaseItem> cases = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            cases.add(caseItem(id, 4201L, 100L + id, "传统零售", "门店陈列与客流"));
        }
        cases.add(caseItem(99L, 4201L, 199L, "软件工具", "帮助小团队使用生成式技术"));
        cases.add(caseItem(100L, 32L, 200L, "软件工具", "跨地区人工智能应用案例"));
        when(caseItemMapper.selectList(any())).thenReturn(cases);
        when(caseTagMapper.selectList(any())).thenReturn(List.of(caseTag(99L, 703L), caseTag(100L, 541L)));

        when(policyMapper.selectList(any())).thenReturn(List.of(
                policy(201L, 4201L, 301L),
                policy(202L, 42L, 302L),
                policy(203L, 1L, 303L),
                policy(204L, 32L, 304L)
        ));
        when(policyTagMapper.selectList(any())).thenReturn(List.of(
                policyTag(201L, 703L), policyTag(202L, 703L),
                policyTag(203L, 703L), policyTag(204L, 703L)
        ));
        when(sourceMapper.selectById(any())).thenAnswer(invocation -> source(invocation.getArgument(0), true));

        var readiness = service.readiness(request(), true);

        assertTrue(readiness.isModelAvailable());
        assertTrue(readiness.isEvidenceAvailable());
        assertEquals(2, readiness.getVerifiedCaseCount());
        assertEquals(3, readiness.getVerifiedPolicyCount());
        assertEquals(2, readiness.getExactRegionCount());
        assertEquals(1, readiness.getParentRegionCount());
        assertEquals(1, readiness.getNationalCount());
        assertEquals(1, readiness.getCrossRegionCount());
        assertFalse(readiness.getReasons().stream().anyMatch(reason -> reason.contains("零售")));
        verify(aiClient, never()).generate(any());
    }

    @Test
    void reportsMissingVerifiedCasesAndPoliciesSeparately() {
        var readiness = service.readiness(request(), true);

        assertFalse(readiness.isEvidenceAvailable());
        assertTrue(readiness.getReasons().contains("无已核验案例"));
        assertTrue(readiness.getReasons().contains("无已核验政策"));
        assertEquals(0, readiness.getVerifiedSourceCount());
        verify(aiClient, never()).generate(any());
    }

    @Test
    void reportsRelatedItemsWhoseSourcesAreNotVerified() {
        when(caseItemMapper.selectList(any())).thenReturn(List.of(caseItem(99L, 4201L, 199L, "软件工具", "人工智能应用")));
        when(policyMapper.selectList(any())).thenReturn(List.of(policy(201L, 4201L, 301L)));
        when(caseTagMapper.selectList(any())).thenReturn(List.of(caseTag(99L, 703L)));
        when(policyTagMapper.selectList(any())).thenReturn(List.of(policyTag(201L, 703L)));
        when(sourceMapper.selectById(any())).thenAnswer(invocation -> source(invocation.getArgument(0), false));

        var readiness = service.readiness(request(), true);

        assertFalse(readiness.isEvidenceAvailable());
        assertEquals(0, readiness.getVerifiedSourceCount());
        assertTrue(readiness.getReasons().stream().anyMatch(reason -> reason.contains("来源未核验")));
    }

    private EntrepreneurshipReadinessRequestDTO request() {
        EntrepreneurshipReadinessRequestDTO request = new EntrepreneurshipReadinessRequestDTO();
        request.setRegionId(4201L);
        request.setIndustryTagId(703L);
        request.setIndustry("人工智能应用");
        return request;
    }

    private Region country() {
        return region(1L, "中国", "country", null);
    }

    private Region province() {
        return region(42L, "湖北省", "province", 1L);
    }

    private Region city() {
        return region(4201L, "武汉市", "city", 42L);
    }

    private Region crossRegion() {
        return region(32L, "江苏省", "province", 1L);
    }

    private Region region(Long id, String name, String level, Long parentId) {
        Region region = new Region();
        region.setId(id);
        region.setName(name);
        region.setLevel(level);
        region.setParentId(parentId);
        return region;
    }

    private CaseItem caseItem(Long id, Long regionId, Long sourceId, String category, String summary) {
        CaseItem item = new CaseItem();
        item.setId(id);
        item.setRegionId(regionId);
        item.setSourceId(sourceId);
        item.setTitle("案例 " + id);
        item.setCategory(category);
        item.setSummary(summary);
        item.setStatus("published");
        item.setAiEvidenceStatus("verified");
        return item;
    }

    private Policy policy(Long id, Long regionId, Long sourceId) {
        Policy item = new Policy();
        item.setId(id);
        item.setRegionId(regionId);
        item.setSourceId(sourceId);
        item.setTitle("人工智能应用创业政策 " + id);
        item.setSummary("支持人工智能应用创业项目");
        item.setStatus("published");
        item.setAiEvidenceStatus("verified");
        return item;
    }

    private Source source(Long id, boolean verified) {
        Source source = new Source();
        source.setId(id);
        source.setTitle("来源 " + id);
        source.setUrl("https://source.example/" + id);
        source.setStatus("published");
        source.setAiEvidenceStatus(verified ? "verified" : "legacy_unverified");
        return source;
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
