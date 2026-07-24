package com.opc.platform.ai.service;

import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.EvidenceReviewUpdateDTO;
import com.opc.platform.ai.entity.AiEvidenceReview;
import com.opc.platform.ai.mapper.AiEvidenceReviewMapper;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceReviewServiceTest {

    private final CaseItemMapper caseItemMapper = mock(CaseItemMapper.class);
    private final PolicyMapper policyMapper = mock(PolicyMapper.class);
    private final SourceMapper sourceMapper = mock(SourceMapper.class);
    private final AiEvidenceReviewMapper reviewMapper = mock(AiEvidenceReviewMapper.class);

    private EvidenceReviewService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceReviewService(caseItemMapper, policyMapper, sourceMapper, reviewMapper);
    }

    @Test
    void caseCannotBeVerifiedUntilItsPublishedSourceIsVerified() {
        CaseItem item = caseItem();
        Source source = source("legacy_unverified");
        when(caseItemMapper.selectById(11L)).thenReturn(item);
        when(sourceMapper.selectById(8L)).thenReturn(source);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.review("case", 11L, update("verified"), admin()));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(caseItemMapper, never()).updateById(any(CaseItem.class));
        verify(reviewMapper, never()).insert(any(AiEvidenceReview.class));
    }

    @Test
    void verifiedSourceRecordsTheResponsibleAdministratorInAuditHistory() {
        Source source = source("legacy_unverified");
        when(sourceMapper.selectById(8L)).thenReturn(source);

        var result = service.review("source", 8L, update("verified"), admin());

        assertEquals("verified", result.getEvidenceStatus());
        verify(sourceMapper).updateById(source);
        ArgumentCaptor<AiEvidenceReview> audit = ArgumentCaptor.forClass(AiEvidenceReview.class);
        verify(reviewMapper).insert(audit.capture());
        assertEquals("verified", source.getAiEvidenceStatus());
        assertEquals(7L, audit.getValue().getAdminId());
        assertEquals("reviewer", audit.getValue().getAdminUsername());
    }

    private EvidenceReviewUpdateDTO update(String status) {
        EvidenceReviewUpdateDTO dto = new EvidenceReviewUpdateDTO();
        dto.setEvidenceStatus(status);
        dto.setNotes("verified against original publication");
        return dto;
    }

    private AuthenticatedAdmin admin() {
        return new AuthenticatedAdmin(7L, "reviewer");
    }

    private CaseItem caseItem() {
        CaseItem item = new CaseItem();
        item.setId(11L);
        item.setTitle("verified case");
        item.setSourceId(8L);
        item.setStatus("published");
        item.setAiEvidenceStatus("legacy_unverified");
        return item;
    }

    private Source source(String evidenceStatus) {
        Source source = new Source();
        source.setId(8L);
        source.setTitle("original publication");
        source.setUrl("https://source.example/8");
        source.setStatus("published");
        source.setAiEvidenceStatus(evidenceStatus);
        return source;
    }
}
