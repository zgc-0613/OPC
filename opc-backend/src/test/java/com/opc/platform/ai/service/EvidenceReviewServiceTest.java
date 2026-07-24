package com.opc.platform.ai.service;

import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.EvidenceReviewBatchItemDTO;
import com.opc.platform.ai.dto.EvidenceReviewBatchUpdateDTO;
import com.opc.platform.ai.dto.EvidenceReviewUpdateDTO;
import com.opc.platform.ai.dto.EvidenceReviewQueryDTO;
import com.opc.platform.ai.vo.EvidenceReviewPreflightVO;
import com.opc.platform.ai.entity.AiEvidenceReview;
import com.opc.platform.ai.mapper.AiEvidenceReviewMapper;
import com.opc.platform.ai.mapper.EvidenceReviewQueueMapper;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class EvidenceReviewServiceTest {

    private final CaseItemMapper caseItemMapper = mock(CaseItemMapper.class);
    private final PolicyMapper policyMapper = mock(PolicyMapper.class);
    private final SourceMapper sourceMapper = mock(SourceMapper.class);
    private final AiEvidenceReviewMapper reviewMapper = mock(AiEvidenceReviewMapper.class);
    private final EvidenceReviewQueueMapper queueMapper = mock(EvidenceReviewQueueMapper.class);

    private EvidenceReviewService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceReviewService(caseItemMapper, policyMapper, sourceMapper, reviewMapper, queueMapper);
        when(caseItemMapper.update(any(), any())).thenReturn(1);
        when(policyMapper.update(any(), any())).thenReturn(1);
        when(sourceMapper.update(any(), any())).thenReturn(1);
        when(caseItemMapper.verifyEvidenceWithEligibleSource(any(), any(), any(), any())).thenReturn(1);
        when(policyMapper.verifyEvidenceWithEligibleSource(any(), any(), any(), any())).thenReturn(1);
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
        verify(sourceMapper).update(any(), any());
        ArgumentCaptor<AiEvidenceReview> audit = ArgumentCaptor.forClass(AiEvidenceReview.class);
        verify(reviewMapper).insert(audit.capture());
        assertEquals("verified", source.getAiEvidenceStatus());
        assertEquals(7L, audit.getValue().getAdminId());
        assertEquals("reviewer", audit.getValue().getAdminUsername());
    }

    @Test
    void deepUnfilteredPagesUseDatabaseOffsetWithoutMergeWindows() {
        EvidenceReviewQueryDTO query = new EvidenceReviewQueryDTO();
        query.setPage(51);
        query.setSize(100);

        when(queueMapper.selectPage(query, 100, 5000)).thenReturn(List.of());
        when(queueMapper.count(query)).thenReturn(0L);

        var page = service.list(query);

        assertEquals(0, page.getItems().size());
        verify(queueMapper).selectPage(query, 100, 5000);
    }

    @Test
    void duplicateBatchTargetsAreRejectedBeforeAnyReviewIsWritten() {
        Source source = source("legacy_unverified");
        when(sourceMapper.selectById(8L)).thenReturn(source);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.reviewBatch(batch("excluded", target("source", 8L), target("source", 8L)), admin())
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(sourceMapper, never()).updateById(any(Source.class));
        verify(reviewMapper, never()).insert(any(AiEvidenceReview.class));
    }

    @Test
    void successfulBatchReviewAuditsEveryItemUnderTheActingAdministrator() {
        CaseItem caseItem = caseItem();
        Source source = source("legacy_unverified");
        when(caseItemMapper.selectById(11L)).thenReturn(caseItem);
        when(sourceMapper.selectById(8L)).thenReturn(source);

        var result = service.reviewBatch(
                batch("excluded", target("case", 11L), target("source", 8L)),
                admin()
        );

        assertEquals(2, result.getProcessedCount());
        ArgumentCaptor<AiEvidenceReview> audits = ArgumentCaptor.forClass(AiEvidenceReview.class);
        verify(reviewMapper, times(2)).insert(audits.capture());
        assertTrue(audits.getAllValues().stream().allMatch(audit -> audit.getAdminId().equals(7L)));
        assertTrue(audits.getAllValues().stream().allMatch(audit -> "reviewer".equals(audit.getAdminUsername())));
        assertTrue(audits.getAllValues().stream().allMatch(audit -> "batch_review".equals(audit.getActionType())));
        assertEquals(1, audits.getAllValues().stream().map(AiEvidenceReview::getOperationId).distinct().count());
    }

    @Test
    void staleEvidenceStatusSnapshotIsRejectedAsAConflict() {
        Source source = source("legacy_unverified");
        when(sourceMapper.selectById(8L)).thenReturn(source);
        EvidenceReviewUpdateDTO dto = update("excluded");
        dto.setExpectedEvidenceStatus("verified");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.review("source", 8L, dto, admin())
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verify(sourceMapper, never()).updateById(any(Source.class));
        verify(reviewMapper, never()).insert(any(AiEvidenceReview.class));
    }

    @Test
    void staleRevisionIsRejectedEvenWhenStatusAndTimestampAreUnchangedWithinSameSecond() {
        Source source = source("legacy_unverified");
        source.setEvidenceRevision(2L);
        when(sourceMapper.selectById(8L)).thenReturn(source);
        EvidenceReviewUpdateDTO dto = update("verified");
        dto.setExpectedVersion(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.review("source", 8L, dto, admin())
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verify(sourceMapper, never()).update(any(), any());
    }

    @Test
    void unsafeSourceUrlCannotBeApprovedAsEvidence() {
        Source source = source("legacy_unverified");
        source.setUrl("javascript:alert(1)");
        when(sourceMapper.selectById(8L)).thenReturn(source);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.review("source", 8L, update("verified"), admin())
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(sourceMapper, never()).update(any(), any());
    }

    @Test
    void sourceWithoutPublisherCannotBeApprovedAsEvidence() {
        Source source = source("legacy_unverified");
        source.setPublisher(" ");
        when(sourceMapper.selectById(8L)).thenReturn(source);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.review("source", 8L, update("verified"), admin())
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("发布机构"));
        verify(sourceMapper, never()).update(any(), any());
    }

    @Test
    void verifiedSourceCannotBeDeletedThroughOrdinaryCrud() {
        Source source = source("verified");
        when(sourceMapper.selectOne(any())).thenReturn(source);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.requireSourceDeletionAllowed(source)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void verifiedCaseCannotBeDeletedThroughOrdinaryCrud() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.requireReviewedItemDeletionAllowed("case", "verified")
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void verifiedSourceDowngradeIsBlockedWhileVerifiedDependenciesRemain() {
        Source source = source("verified");
        when(sourceMapper.selectById(8L)).thenReturn(source);
        when(caseItemMapper.selectCount(any())).thenReturn(1L);
        when(policyMapper.selectCount(any())).thenReturn(0L);
        EvidenceReviewUpdateDTO dto = update("excluded");
        dto.setExpectedEvidenceStatus("verified");
        dto.setReason("source is no longer trustworthy");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.review("source", 8L, dto, admin())
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(sourceMapper, never()).updateById(any(Source.class));
        verify(reviewMapper, never()).insert(any(AiEvidenceReview.class));
    }

    @Test
    void confirmedSourceDowngradeInvalidatesDependenciesUnderOneOperation() {
        Source source = source("verified");
        CaseItem caseItem = caseItem();
        caseItem.setAiEvidenceStatus("verified");
        Policy policy = new Policy();
        policy.setId(12L);
        policy.setTitle("verified policy");
        policy.setSourceId(8L);
        policy.setStatus("published");
        policy.setAiEvidenceStatus("verified");
        policy.setEvidenceRevision(0L);
        policy.setUpdatedAt(LocalDateTime.of(2026, 7, 25, 0, 0));
        when(sourceMapper.selectById(8L)).thenReturn(source);
        when(caseItemMapper.selectCount(any())).thenReturn(1L);
        when(policyMapper.selectCount(any())).thenReturn(1L);
        when(caseItemMapper.selectList(any())).thenReturn(List.of(caseItem));
        when(policyMapper.selectList(any())).thenReturn(List.of(policy));
        EvidenceReviewUpdateDTO dto = update("excluded");
        dto.setExpectedEvidenceStatus("verified");
        dto.setReason("source withdrawn");
        dto.setCascade(true);

        service.review("source", 8L, dto, admin());

        assertEquals("legacy_unverified", caseItem.getAiEvidenceStatus());
        assertEquals("legacy_unverified", policy.getAiEvidenceStatus());
        ArgumentCaptor<AiEvidenceReview> audits = ArgumentCaptor.forClass(AiEvidenceReview.class);
        verify(reviewMapper, times(3)).insert(audits.capture());
        assertEquals(1, audits.getAllValues().stream().map(AiEvidenceReview::getOperationId).distinct().count());
        assertTrue(audits.getAllValues().stream().anyMatch(audit -> "dependency_invalidated".equals(audit.getActionType())));
    }

    @Test
    void batchDowngradeProcessesChildrenBeforeSourceRegardlessOfInputOrder() {
        Source source = source("verified");
        CaseItem caseItem = caseItem();
        caseItem.setAiEvidenceStatus("verified");
        when(sourceMapper.selectById(8L)).thenReturn(source);
        when(caseItemMapper.selectById(11L)).thenReturn(caseItem);
        when(caseItemMapper.selectList(any())).thenReturn(List.of(caseItem));
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(sourceMapper.update(any(), any())).thenReturn(1);
        when(caseItemMapper.update(any(), any())).thenReturn(1);

        EvidenceReviewBatchItemDTO sourceTarget = target("source", 8L);
        sourceTarget.setExpectedEvidenceStatus("verified");
        EvidenceReviewBatchItemDTO caseTarget = target("case", 11L);
        caseTarget.setExpectedEvidenceStatus("verified");
        EvidenceReviewBatchUpdateDTO dto = batch("excluded", sourceTarget, caseTarget);
        dto.setCascade(true);

        var result = service.reviewBatch(dto, admin());

        assertEquals(2, result.getProcessedCount());
        assertEquals("legacy_unverified", caseItem.getAiEvidenceStatus());
        assertEquals("excluded", source.getAiEvidenceStatus());
    }

    @Test
    void batchLegacyDowngradeUsesTheSameChildFirstOrdering() {
        Source source = source("verified");
        CaseItem caseItem = caseItem();
        caseItem.setAiEvidenceStatus("verified");
        when(sourceMapper.selectById(8L)).thenReturn(source);
        when(caseItemMapper.selectById(11L)).thenReturn(caseItem);
        when(caseItemMapper.selectList(any())).thenReturn(List.of(caseItem));
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(sourceMapper.update(any(), any())).thenReturn(1);
        when(caseItemMapper.update(any(), any())).thenReturn(1);

        EvidenceReviewBatchItemDTO sourceTarget = target("source", 8L);
        sourceTarget.setExpectedEvidenceStatus("verified");
        EvidenceReviewBatchItemDTO caseTarget = target("case", 11L);
        caseTarget.setExpectedEvidenceStatus("verified");
        EvidenceReviewBatchUpdateDTO dto = batch("legacy_unverified", sourceTarget, caseTarget);
        dto.setCascade(true);

        var result = service.reviewBatch(dto, admin());

        assertEquals(2, result.getProcessedCount());
        assertEquals("legacy_unverified", caseItem.getAiEvidenceStatus());
        assertEquals("legacy_unverified", source.getAiEvidenceStatus());
    }

    @Test
    void editingVerifiedCaseInvalidatesItAndRecordsAnAutomaticAudit() {
        CaseItem caseItem = caseItem();
        caseItem.setAiEvidenceStatus("verified");

        service.invalidateCaseAfterEvidenceEdit(caseItem, admin());

        assertEquals("legacy_unverified", caseItem.getAiEvidenceStatus());
        ArgumentCaptor<AiEvidenceReview> audit = ArgumentCaptor.forClass(AiEvidenceReview.class);
        verify(reviewMapper).insert(audit.capture());
        assertEquals("content_invalidated", audit.getValue().getActionType());
        assertEquals("verified", audit.getValue().getPreviousStatus());
        assertEquals("legacy_unverified", audit.getValue().getNewStatus());
        assertEquals("reviewer", audit.getValue().getAdminUsername());
    }

    @Test
    void editingVerifiedSourceInvalidatesSourceAndDependenciesUnderOneOperation() {
        Source source = source("verified");
        CaseItem caseItem = caseItem();
        caseItem.setAiEvidenceStatus("verified");
        Policy policy = new Policy();
        policy.setId(12L);
        policy.setSourceId(8L);
        policy.setAiEvidenceStatus("verified");
        when(caseItemMapper.selectList(any())).thenReturn(List.of(caseItem));
        when(policyMapper.selectList(any())).thenReturn(List.of(policy));

        service.invalidateSourceAfterEvidenceEdit(source, admin());

        assertEquals("legacy_unverified", source.getAiEvidenceStatus());
        assertEquals("legacy_unverified", caseItem.getAiEvidenceStatus());
        assertEquals("legacy_unverified", policy.getAiEvidenceStatus());
        ArgumentCaptor<AiEvidenceReview> audits = ArgumentCaptor.forClass(AiEvidenceReview.class);
        verify(reviewMapper, times(3)).insert(audits.capture());
        assertEquals(1, audits.getAllValues().stream().map(AiEvidenceReview::getOperationId).distinct().count());
        assertTrue(audits.getAllValues().stream().anyMatch(audit -> "content_invalidated".equals(audit.getActionType())));
        assertEquals(2, audits.getAllValues().stream().filter(audit -> "dependency_invalidated".equals(audit.getActionType())).count());
    }

    @Test
    void childCasFailureAbortsSourceCascadeInsteadOfBeingSilentlySkipped() {
        Source source = source("verified");
        CaseItem caseItem = caseItem();
        caseItem.setAiEvidenceStatus("verified");
        when(sourceMapper.selectById(8L)).thenReturn(source);
        when(caseItemMapper.selectCount(any())).thenReturn(1L);
        when(policyMapper.selectCount(any())).thenReturn(0L);
        when(caseItemMapper.selectList(any())).thenReturn(List.of(caseItem));
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(caseItemMapper.update(any(), any())).thenReturn(0);
        EvidenceReviewUpdateDTO dto = update("excluded");
        dto.setExpectedEvidenceStatus("verified");
        dto.setReason("source withdrawn");
        dto.setCascade(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.review("source", 8L, dto, admin())
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }

    @Test
    void preflightAllowsDependentCaseWhenItsReviewableSourceIsVerifiedFirstInSameBatch() {
        Source source = source("legacy_unverified");
        CaseItem caseItem = caseItem();
        when(sourceMapper.selectById(8L)).thenReturn(source);
        when(caseItemMapper.selectById(11L)).thenReturn(caseItem);
        when(caseItemMapper.selectList(any())).thenReturn(List.of(caseItem));
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.selectList(any())).thenReturn(List.of());
        EvidenceReviewBatchUpdateDTO dto = batch("verified", target("case", 11L), target("source", 8L));

        EvidenceReviewPreflightVO result = service.preflight(dto);

        assertEquals(2, result.getActionableCount());
        assertEquals(0, result.getBlockedCount());
    }

    @Test
    void detailIncludesReviewChecksAndDistinguishableAuditHistory() {
        Source source = source("verified");
        AiEvidenceReview history = new AiEvidenceReview();
        history.setId(41L);
        history.setItemType("source");
        history.setItemId(8L);
        history.setPreviousStatus("legacy_unverified");
        history.setNewStatus("verified");
        history.setAdminUsername("reviewer");
        history.setActionType("single_review");
        history.setOperationId("operation-41");
        when(sourceMapper.selectById(8L)).thenReturn(source);
        when(caseItemMapper.selectList(any())).thenReturn(List.of());
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.selectList(any())).thenReturn(List.of(history));

        var detail = service.detail("source", 8L);

        assertTrue(detail.isReviewable());
        assertTrue(detail.getChecks().stream().allMatch(com.opc.platform.ai.vo.EvidenceReviewCheckVO::isPassed));
        assertEquals("single_review", detail.getHistory().get(0).getActionType());
        assertEquals("operation-41", detail.getHistory().get(0).getOperationId());
    }

    private EvidenceReviewUpdateDTO update(String status) {
        EvidenceReviewUpdateDTO dto = new EvidenceReviewUpdateDTO();
        dto.setEvidenceStatus(status);
        dto.setExpectedEvidenceStatus("legacy_unverified");
        dto.setExpectedUpdatedAt(LocalDateTime.of(2026, 7, 25, 0, 0));
        dto.setExpectedVersion(0L);
        dto.setNotes("verified against original publication");
        return dto;
    }

    private EvidenceReviewBatchUpdateDTO batch(String status, EvidenceReviewBatchItemDTO... items) {
        EvidenceReviewBatchUpdateDTO dto = new EvidenceReviewBatchUpdateDTO();
        dto.setEvidenceStatus(status);
        dto.setItems(List.of(items));
        dto.setReason("batch review decision");
        return dto;
    }

    private EvidenceReviewBatchItemDTO target(String itemType, Long itemId) {
        EvidenceReviewBatchItemDTO item = new EvidenceReviewBatchItemDTO();
        item.setItemType(itemType);
        item.setItemId(itemId);
        item.setExpectedEvidenceStatus("legacy_unverified");
        item.setExpectedUpdatedAt(LocalDateTime.of(2026, 7, 25, 0, 0));
        item.setExpectedVersion(0L);
        return item;
    }

    private AuthenticatedAdmin admin() {
        return new AuthenticatedAdmin(7L, "reviewer");
    }

    private CaseItem caseItem() {
        CaseItem item = new CaseItem();
        item.setId(11L);
        item.setTitle("verified case");
        item.setSummary("Evidence-backed case summary");
        item.setSourceId(8L);
        item.setStatus("published");
        item.setAiEvidenceStatus("legacy_unverified");
        item.setEvidenceRevision(0L);
        item.setUpdatedAt(LocalDateTime.of(2026, 7, 25, 0, 0));
        return item;
    }

    private Source source(String evidenceStatus) {
        Source source = new Source();
        source.setId(8L);
        source.setTitle("original publication");
        source.setPublisher("official publisher");
        source.setUrl("https://source.example/8");
        source.setStatus("published");
        source.setAiEvidenceStatus(evidenceStatus);
        source.setEvidenceRevision(0L);
        source.setUpdatedAt(LocalDateTime.of(2026, 7, 25, 0, 0));
        return source;
    }
}
