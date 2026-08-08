package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhaseThreeEvidenceResolverTest {

    @Test
    void selectedCaseResolvesCurrentEntitySourceAndProvenanceLink() throws Exception {
        CaseItemMapper cases = mock(CaseItemMapper.class);
        PolicyMapper policies = mock(PolicyMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        CaseItem selected = eligibleCase(1001L, 9001L);
        Source source = eligibleSource(9001L);
        when(cases.selectBatchIds(List.of(1001L))).thenReturn(List.of(selected));
        when(sources.selectBatchIds(List.of(9001L))).thenReturn(List.of(source));
        PhaseThreeEvidenceResolver resolver = new PhaseThreeEvidenceResolver(
                cases, policies, sources, new ObjectMapper());

        var bundle = resolver.resolve(
                Set.of(), Set.of(), Set.of(),
                new ObjectMapper().readTree("""
                        {"version":"phase3-task-v1","taskType":"case_analysis",
                         "caseIds":[1001],"comparisonDimensions":[],"outputDepth":"standard"}
                        """)
        );

        assertEquals(Set.of(1001L), bundle.caseIds());
        assertEquals(Set.of(9001L), bundle.sourceIds());
        assertEquals(List.of(new com.opc.platform.ai.tool.PhaseThreeEvidenceBundle.CaseSourceLink(1001L, 9001L)),
                bundle.caseSourceLinks());
        assertEquals("来源标题", bundle.source(9001L).title());
        assertEquals("发布者", bundle.source(9001L).publisher());
        assertEquals("https://example.invalid/source/9001", bundle.source(9001L).url());
        assertTrue(bundle.cases().get(0).contentHash().matches("sha256:[0-9a-f]{64}"));
        assertTrue(bundle.sources().get(0).contentHash().matches("sha256:[0-9a-f]{64}"));
    }

    private CaseItem eligibleCase(long id, long sourceId) {
        CaseItem item = new CaseItem();
        item.setId(id);
        item.setSourceId(sourceId);
        item.setTitle("案例标题");
        item.setSummary("案例摘要");
        item.setStatus("published");
        item.setAiEvidenceStatus("verified");
        item.setEvidenceRevision(1L);
        return item;
    }

    private Source eligibleSource(long id) {
        Source source = new Source();
        source.setId(id);
        source.setTitle("来源标题");
        source.setPublisher("发布者");
        source.setUrl("https://example.invalid/source/" + id);
        source.setStatus("published");
        source.setAiEvidenceStatus("verified");
        source.setEvidenceRevision(2L);
        return source;
    }
}
