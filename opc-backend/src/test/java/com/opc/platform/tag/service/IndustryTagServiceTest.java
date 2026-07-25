package com.opc.platform.tag.service;

import com.opc.platform.casetag.mapper.CaseTagMapper;
import com.opc.platform.casetag.entity.CaseTag;
import com.opc.platform.policyindustrytag.entity.PolicyIndustryTag;
import com.opc.platform.policyindustrytag.mapper.PolicyIndustryTagMapper;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import com.opc.platform.tagalias.entity.TagAlias;
import com.opc.platform.tagalias.mapper.TagAliasMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndustryTagServiceTest {

    private final TagMapper tagMapper = mock(TagMapper.class);
    private final TagAliasMapper aliasMapper = mock(TagAliasMapper.class);
    private final CaseTagMapper caseTagMapper = mock(CaseTagMapper.class);
    private final PolicyIndustryTagMapper policyIndustryTagMapper = mock(PolicyIndustryTagMapper.class);
    private IndustryTagService service;

    @BeforeEach
    void setUp() {
        service = new IndustryTagService(
                tagMapper,
                aliasMapper,
                caseTagMapper,
                policyIndustryTagMapper
        );
        when(tagMapper.selectList(any())).thenReturn(List.of(industry(703L, "人工智能应用"), industry(517L, "智能零售")));
        when(aliasMapper.selectList(any())).thenReturn(List.of(alias(703L, "AI应用"), alias(703L, "AIGC")));
    }

    @Test
    void exactIndustryTagIdWinsBeforeTextResolution() {
        when(tagMapper.selectById(703L)).thenReturn(industry(703L, "人工智能应用"));

        var resolved = service.resolve(703L, "完全不同的文本", false);

        assertEquals(703L, resolved.tagId());
        assertEquals("tag_id", resolved.method());
        assertEquals(1.0, resolved.confidence());
        assertFalse(resolved.requiresConfirmation());
    }

    @Test
    void exactAliasMapsAiApplicationToCanonicalIndustry() {
        var resolved = service.resolve(null, "AIGC", false);

        assertEquals(703L, resolved.tagId());
        assertEquals("alias", resolved.method());
        assertTrue(resolved.confidence() >= 0.95);
        assertFalse(resolved.requiresConfirmation());
    }

    @Test
    void normalizedFuzzyMatchIsDeterministic() {
        var resolved = service.resolve(null, "人工智能应用行业", false);

        assertEquals(703L, resolved.tagId());
        assertEquals("fuzzy", resolved.method());
        assertTrue(resolved.requiresConfirmation());
    }

    @Test
    void deterministicResolutionNeverCallsTheProvider() {
        var resolved = service.resolve(null, "生成式算法产品", true);

        assertEquals(null, resolved.tagId());
        assertEquals("unresolved", resolved.method());
    }

    @Test
    void publicIndustryListIncludesPolicyAndCaseUsageCounts() {
        CaseTag caseTag = new CaseTag();
        caseTag.setCaseId(11L);
        caseTag.setTagId(703L);
        PolicyIndustryTag firstPolicyTag = new PolicyIndustryTag();
        firstPolicyTag.setPolicyId(21L);
        firstPolicyTag.setIndustryTagId(703L);
        PolicyIndustryTag secondPolicyTag = new PolicyIndustryTag();
        secondPolicyTag.setPolicyId(22L);
        secondPolicyTag.setIndustryTagId(703L);
        when(caseTagMapper.selectList(any())).thenReturn(List.of(caseTag));
        when(policyIndustryTagMapper.selectList(any())).thenReturn(List.of(firstPolicyTag, secondPolicyTag));

        var industries = service.listIndustries();

        var ai = industries.stream().filter(item -> item.tagId().equals(703L)).findFirst().orElseThrow();
        assertEquals(1, ai.caseUsageCount());
        assertEquals(2, ai.policyUsageCount());
    }

    private Tag industry(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setTagType("common");
        tag.setIsIndustry(true);
        return tag;
    }

    private TagAlias alias(Long tagId, String value) {
        TagAlias alias = new TagAlias();
        alias.setTagId(tagId);
        alias.setAlias(value);
        alias.setNormalizedAlias(IndustryTagService.normalize(value));
        return alias;
    }
}
