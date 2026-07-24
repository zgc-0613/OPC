package com.opc.platform.tag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.casetag.mapper.CaseTagMapper;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import com.opc.platform.tagalias.entity.TagAlias;
import com.opc.platform.tagalias.mapper.TagAliasMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndustryTagServiceTest {

    private final TagMapper tagMapper = mock(TagMapper.class);
    private final TagAliasMapper aliasMapper = mock(TagAliasMapper.class);
    private final CaseTagMapper caseTagMapper = mock(CaseTagMapper.class);
    private final PolicyTagMapper policyTagMapper = mock(PolicyTagMapper.class);
    private final AiClient aiClient = mock(AiClient.class);

    private IndustryTagService service;

    @BeforeEach
    void setUp() {
        service = new IndustryTagService(
                tagMapper,
                aliasMapper,
                caseTagMapper,
                policyTagMapper,
                aiClient,
                new ObjectMapper()
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
        assertFalse(resolved.requiresConfirmation());
    }

    @Test
    void aiResolutionCanOnlySelectOneOfTheProvidedCandidateTagIds() {
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(aiClient.generate(any())).thenReturn(new AiProviderResponse(
                "{\"tagId\":703,\"confidence\":0.91}",
                30,
                8,
                38,
                120,
                "req-industry"
        ));

        var resolved = service.resolve(null, "生成式算法产品", true);

        assertEquals(703L, resolved.tagId());
        assertEquals("ai", resolved.method());
        assertFalse(resolved.requiresConfirmation());
        ArgumentCaptor<AiProviderRequest> request = ArgumentCaptor.forClass(AiProviderRequest.class);
        verify(aiClient).generate(request.capture());
        assertTrue(request.getValue().userPrompt().contains("703"));
        assertTrue(request.getValue().userPrompt().contains("517"));
    }

    @Test
    void lowConfidenceAiResolutionRequiresUserConfirmation() {
        when(aiClient.descriptor()).thenReturn(new AiProviderDescriptor("deepseek", "configured-model", true));
        when(aiClient.generate(any())).thenReturn(new AiProviderResponse("{\"tagId\":703,\"confidence\":0.55}"));

        var resolved = service.resolve(null, "模糊的新方向", true);

        assertEquals(703L, resolved.tagId());
        assertEquals("ai", resolved.method());
        assertTrue(resolved.requiresConfirmation());
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
