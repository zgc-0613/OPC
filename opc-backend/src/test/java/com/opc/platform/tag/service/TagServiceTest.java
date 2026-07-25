package com.opc.platform.tag.service;

import com.opc.platform.casetag.mapper.CaseTagMapper;
import com.opc.platform.policyindustrytag.mapper.PolicyIndustryTagMapper;
import com.opc.platform.tag.dto.TagUpdateDTO;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import com.opc.platform.tagalias.mapper.TagAliasMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TagServiceTest {

    @Test
    void administratorIndustryFlagMakesAPolicyTagAvailableToThePublicIndustryList() {
        TagMapper tagMapper = mock(TagMapper.class);
        TagAliasMapper aliasMapper = mock(TagAliasMapper.class);
        CaseTagMapper caseTagMapper = mock(CaseTagMapper.class);
        PolicyIndustryTagMapper policyIndustryTagMapper = mock(PolicyIndustryTagMapper.class);
        AtomicReference<Tag> stored = new AtomicReference<>(tag(27L, false));
        when(tagMapper.selectById(27L)).thenAnswer(invocation -> stored.get());
        when(tagMapper.selectCount(any())).thenReturn(0L);
        when(tagMapper.updateById(any(Tag.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(tagMapper.selectList(any())).thenAnswer(invocation -> List.of(stored.get()));
        when(aliasMapper.selectList(any())).thenReturn(List.of());
        when(caseTagMapper.selectList(any())).thenReturn(List.of());
        when(policyIndustryTagMapper.selectList(any())).thenReturn(List.of());

        TagUpdateDTO update = new TagUpdateDTO();
        update.setName("人工智能政策");
        update.setTagType("policy");
        update.setIsIndustry(true);
        update.setSortOrder(0);
        new TagService(tagMapper).updateTag(27L, update);

        IndustryTagService industryTags = new IndustryTagService(
                tagMapper, aliasMapper, caseTagMapper, policyIndustryTagMapper
        );
        var publicIndustries = industryTags.listIndustries();

        assertEquals(1, publicIndustries.size());
        assertEquals(27L, publicIndustries.get(0).tagId());
        assertTrue(stored.get().getIsIndustry());
    }

    private Tag tag(Long id, boolean isIndustry) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName("人工智能政策");
        tag.setTagType("policy");
        tag.setIsIndustry(isIndustry);
        tag.setSortOrder(0);
        return tag;
    }
}
