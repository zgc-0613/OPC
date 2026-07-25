package com.opc.platform.caseitem.service;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.opc.platform.caseitem.dto.CaseItemQueryDTO;
import com.opc.platform.caseitem.dto.CaseItemUpdateDTO;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.region.entity.Region;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.Collections;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class CaseItemServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "case-item-test"),
                CaseItem.class
        );
    }

    @Mock
    private CaseItemMapper caseItemMapper;

    @Mock
    private RegionMapper regionMapper;

    @Mock
    private SourceMapper sourceMapper;

    private CaseItemService service;

    @BeforeEach
    void setUp() {
        service = new CaseItemService(
                caseItemMapper,
                regionMapper,
                sourceMapper,
                org.mockito.Mockito.mock(com.opc.platform.tag.mapper.TagMapper.class),
                org.mockito.Mockito.mock(com.opc.platform.casetag.mapper.CaseTagMapper.class),
                org.mockito.Mockito.mock(com.opc.platform.ai.service.EvidenceReviewService.class)
        );
    }

    @Test
    void publicListAlwaysReadsPublishedCases() {
        CaseItemQueryDTO query = new CaseItemQueryDTO();
        query.setStatus("draft");
        CaseItem published = new CaseItem();
        published.setId(7L);
        published.setRegionId(1L);
        published.setSourceId(2L);
        published.setStatus("published");

        when(caseItemMapper.selectList(argThat(wrapper -> hasParameter(wrapper, "published"))))
                .thenReturn(List.of(published));
        when(regionMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        when(sourceMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        var result = service.listPublicCaseItems(query);

        assertEquals(1, result.size());
        assertEquals("published", result.get(0).getStatus());
    }

    @Test
    void publicDetailHidesDraftCases() {
        CaseItem draft = new CaseItem();
        draft.setId(9L);
        draft.setStatus("draft");
        when(caseItemMapper.selectById(9L)).thenReturn(draft);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getPublicCaseItemDetail(9L)
        );

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void ordinaryUpdateReportsConflictWhenTheLockedRowCannotBeUpdated() {
        CaseItem current = editableCase();
        when(caseItemMapper.selectById(9L)).thenReturn(current);
        when(caseItemMapper.selectByIdForUpdate(9L)).thenReturn(current);
        when(regionMapper.selectById(1L)).thenReturn(new Region());
        when(sourceMapper.selectByIdForUpdate(2L)).thenReturn(new Source());
        when(caseItemMapper.updateById(any(CaseItem.class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateCaseItem(9L, updateDto(), new AuthenticatedAdmin(7L, "reviewer"))
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }

    @Test
    void updateLocksTheSourceBeforeTheCaseRow() {
        CaseItem current = editableCase();
        current.setCategory(null);
        when(caseItemMapper.selectById(9L)).thenReturn(current);
        when(caseItemMapper.selectByIdForUpdate(9L)).thenReturn(current);
        when(regionMapper.selectById(1L)).thenReturn(new Region());
        when(sourceMapper.selectByIdForUpdate(2L)).thenReturn(new Source());
        when(caseItemMapper.updateById(any(CaseItem.class))).thenReturn(1);

        CaseItemUpdateDTO dto = updateDto();
        dto.setCategory(null);
        service.updateCaseItem(9L, dto, new AuthenticatedAdmin(7L, "reviewer"));

        var order = inOrder(sourceMapper, caseItemMapper);
        order.verify(sourceMapper).selectByIdForUpdate(2L);
        order.verify(caseItemMapper).selectByIdForUpdate(9L);
    }

    @Test
    void updateRejectsMissingConcurrencySnapshot() {
        CaseItem current = editableCase();
        when(caseItemMapper.selectById(9L)).thenReturn(current);
        CaseItemUpdateDTO dto = updateDto();
        dto.setExpectedEvidenceRevision(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateCaseItem(9L, dto, new AuthenticatedAdmin(7L, "reviewer"))
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }

    @Test
    void ordinaryDeleteReportsConflictWhenTheRowChangedAfterItWasRead() {
        CaseItem current = editableCase();
        when(caseItemMapper.selectById(9L)).thenReturn(current);
        when(caseItemMapper.selectByIdForUpdate(9L)).thenReturn(current);
        when(sourceMapper.selectByIdForUpdate(2L)).thenReturn(new Source());
        when(caseItemMapper.deleteById(9L)).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.deleteCaseItem(9L, 3L, LocalDateTime.of(2026, 7, 25, 2, 0))
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verify(caseItemMapper).deleteById(9L);
    }

    private CaseItem editableCase() {
        CaseItem item = new CaseItem();
        item.setId(9L);
        item.setTitle("Case");
        item.setRegionId(1L);
        item.setCategory("software");
        item.setSourceId(2L);
        item.setSummary("Summary");
        item.setAccessedAt(LocalDate.of(2026, 7, 25));
        item.setStatus("published");
        item.setAiEvidenceStatus("legacy_unverified");
        item.setEvidenceRevision(3L);
        item.setUpdatedAt(LocalDateTime.of(2026, 7, 25, 2, 0));
        return item;
    }

    private CaseItemUpdateDTO updateDto() {
        CaseItemUpdateDTO dto = new CaseItemUpdateDTO();
        dto.setTitle("Case updated");
        dto.setRegionId(1L);
        dto.setCategory("software");
        dto.setSourceId(2L);
        dto.setSummary("Summary");
        dto.setAccessedAt(LocalDate.of(2026, 7, 25));
        dto.setStatus("published");
        dto.setExpectedEvidenceRevision(3L);
        dto.setExpectedUpdatedAt(LocalDateTime.of(2026, 7, 25, 2, 0));
        return dto;
    }

    private boolean hasParameter(Wrapper<CaseItem> wrapper, String value) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper)) {
            return false;
        }
        abstractWrapper.getSqlSegment();
        return abstractWrapper.getParamNameValuePairs().containsValue(value);
    }
}
