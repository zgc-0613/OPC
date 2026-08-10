package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhaseThreeSelectedEvidenceValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsSelectedCaseOnlyWhenItsLockedSourceChainIsPublishedVerifiedAndProvenanced() throws Exception {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        CaseItem caseItem = eligibleCase(101L, 9001L);
        Source source = eligibleSource(9001L, "https://example.org/case-101");
        when(cases.selectByIdForUpdate(101L)).thenReturn(caseItem);
        when(sources.selectByIdForUpdate(9001L)).thenReturn(source);

        PhaseThreeSelectedEvidenceValidator validator = new PhaseThreeSelectedEvidenceValidator(cases, sources);
        PhaseThreeTaskContext context = context("case_analysis", "\"caseIds\":[101]");

        assertDoesNotThrow(() -> validator.validate(context));
        verify(cases).selectByIdForUpdate(101L);
        verify(sources).selectByIdForUpdate(9001L);
    }

    @Test
    void rejectsUnverifiedSelectedCaseBeforeItCanReachItsSourceOrCreateResearch() throws Exception {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        CaseItem caseItem = eligibleCase(101L, 9001L);
        caseItem.setAiEvidenceStatus("pending");
        when(cases.selectByIdForUpdate(101L)).thenReturn(caseItem);

        PhaseThreeSelectedEvidenceValidator validator = new PhaseThreeSelectedEvidenceValidator(cases, sources);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> validator.validate(context("case_analysis", "\"caseIds\":[101]")));

        assertEquals("PHASE3_CASE_NOT_ELIGIBLE", exception.getMessage());
        verify(sources, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    void rejectsSelectedSourceWhenItsProvenanceIsNotASafeHttpLink() throws Exception {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        Source source = eligibleSource(9004L, "file:///private/evidence.pdf");
        when(sources.selectByIdForUpdate(9004L)).thenReturn(source);

        PhaseThreeSelectedEvidenceValidator validator = new PhaseThreeSelectedEvidenceValidator(cases, sources);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> validator.validate(context("source_verification", "\"sourceId\":9004")));

        assertEquals("PHASE3_SOURCE_NOT_ELIGIBLE", exception.getMessage());
    }

    @Test
    void validatesTechnologyTagAgainstThePublishedTechnologyTaxonomy() throws Exception {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        TagMapper tags = mock(TagMapper.class);
        Tag technology = new Tag();
        technology.setId(91L);
        technology.setTagType("technology");
        technology.setName("检索增强生成");
        when(tags.selectById(91L)).thenReturn(technology);

        PhaseThreeSelectedEvidenceValidator validator =
                new PhaseThreeSelectedEvidenceValidator(cases, sources, tags);
        assertDoesNotThrow(() -> validator.validate(context(
                "technology_assessment", "\"technologyTagId\":91")));

        technology.setTagType("case");
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate(context(
                "technology_assessment", "\"technologyTagId\":91")));
        assertEquals("PHASE3_TECHNOLOGY_TAG_NOT_ELIGIBLE", exception.getMessage());
    }

    private PhaseThreeTaskContext context(String taskType, String selection) throws Exception {
        String json = "{\"version\":\"phase3-task-v1\",\"taskType\":\"" + taskType
                + "\"," + selection + ",\"comparisonDimensions\":[]}";
        return new PhaseThreeTaskContext(taskType, objectMapper.readTree(json), json, "hash");
    }

    private CaseItem eligibleCase(Long id, Long sourceId) {
        CaseItem item = new CaseItem();
        item.setId(id);
        item.setSourceId(sourceId);
        item.setStatus("published");
        item.setAiEvidenceStatus("verified");
        item.setEvidenceRevision(1L);
        return item;
    }

    private Source eligibleSource(Long id, String url) {
        Source source = new Source();
        source.setId(id);
        source.setStatus("published");
        source.setAiEvidenceStatus("verified");
        source.setEvidenceRevision(1L);
        source.setTitle("Verified source");
        source.setPublisher("Public publisher");
        source.setUrl(url);
        return source;
    }
}
