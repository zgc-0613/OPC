package com.opc.platform.source.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.source.dto.SourceCreateDTO;
import com.opc.platform.source.dto.SourceUpdateDTO;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.source.vo.SourceVO;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.service.EvidenceReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "source-test"),
                Source.class
        );
    }

    @Mock
    private SourceMapper sourceMapper;

    @Mock
    private EvidenceReviewService evidenceReviewService;

    private SourceService service;

    @BeforeEach
    void setUp() {
        service = new SourceService(sourceMapper, evidenceReviewService);
    }

    @Test
    void duplicateCreateIdentifiesTheExistingSource() {
        when(sourceMapper.selectOne(any())).thenReturn(source(42L, "Policy Daily"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.createSource(createDto(" Policy Daily "))
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Policy Daily"));
        assertTrue(exception.getMessage().contains("42"));
        verify(sourceMapper, never()).insert(any(Source.class));
    }

    @Test
    void renameConflictIdentifiesTheExistingSource() {
        Source current = source(7L, "Old Source");
        when(sourceMapper.selectByIdForUpdate(7L)).thenReturn(current);
        when(sourceMapper.selectOne(any())).thenReturn(source(42L, "Policy Daily"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateSource(7L, updateDto("Policy Daily"), admin())
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Policy Daily"));
        assertTrue(exception.getMessage().contains("42"));
        verify(sourceMapper, never()).updateById(any(Source.class));
    }

    @Test
    void renameWithoutConflictStoresTheTrimmedTitle() {
        Source current = source(7L, "Old Source");
        when(sourceMapper.selectByIdForUpdate(7L)).thenReturn(current);
        when(sourceMapper.selectById(7L)).thenReturn(current);
        when(sourceMapper.selectOne(any())).thenReturn(null);
        when(sourceMapper.updateById(current)).thenReturn(1);

        service.updateSource(7L, updateDto("  Renamed Source  "), admin());

        ArgumentCaptor<Source> captor = ArgumentCaptor.forClass(Source.class);
        verify(sourceMapper).updateById(captor.capture());
        assertEquals("Renamed Source", captor.getValue().getTitle());
    }

    @Test
    void publicListOnlyReadsPublishedSources() {
        Source published = source(8L, "Published Source");
        published.setStatus("published");
        when(sourceMapper.selectList(argThat(wrapper -> hasParameter(wrapper, "published"))))
                .thenReturn(List.of(published));

        var result = service.listPublicSources();

        assertEquals(1, result.size());
        assertEquals("published", result.get(0).getStatus());
    }

    @Test
    void legacyActiveStatusCannotBeSavedByTheAdministratorApi() {
        Source current = source(7L, "Legacy Source");
        when(sourceMapper.selectByIdForUpdate(7L)).thenReturn(current);
        when(sourceMapper.selectOne(any())).thenReturn(null);
        SourceUpdateDTO dto = updateDto("Legacy Source");
        dto.setStatus("active");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateSource(7L, dto, admin())
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(sourceMapper, never()).updateById(any(Source.class));
    }

    @Test
    void ordinaryCreateCannotPromoteSourceToVerifiedEvidence() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SourceCreateDTO dto = objectMapper.readValue("""
                {
                  "title": "Governed Source",
                  "sourceType": "web",
                  "accessedAt": "2026-07-25",
                  "status": "published",
                  "aiEvidenceStatus": "verified"
                }
                """, SourceCreateDTO.class);
        AtomicReference<Source> inserted = new AtomicReference<>();
        when(sourceMapper.selectOne(any())).thenReturn(null);
        when(sourceMapper.insert(any(Source.class))).thenAnswer(invocation -> {
            Source source = invocation.getArgument(0);
            source.setId(91L);
            inserted.set(source);
            return 1;
        });
        when(sourceMapper.selectById(91L)).thenAnswer(invocation -> inserted.get());

        SourceVO result = service.createSource(dto);

        assertEquals("legacy_unverified", result.getAiEvidenceStatus());
        assertEquals("legacy_unverified", inserted.get().getAiEvidenceStatus());
    }

    @Test
    void changingVerifiedSourceEvidenceFieldsUsesCentralInvalidationService() {
        Source current = source(7L, "Verified Source");
        current.setAiEvidenceStatus("verified");
        when(sourceMapper.selectByIdForUpdate(7L)).thenReturn(current);
        when(sourceMapper.selectById(7L)).thenReturn(current);
        when(sourceMapper.selectOne(any())).thenReturn(null);
        when(sourceMapper.updateById(current)).thenReturn(1);
        SourceUpdateDTO dto = updateDto("Renamed Verified Source");

        service.updateSource(7L, dto, admin());

        verify(evidenceReviewService).invalidateSourceAfterEvidenceEdit(current, admin());
        verify(sourceMapper).updateById(current);
    }

    @Test
    void changingVerifiedSourceNotesAlsoInvalidatesEvidence() {
        Source current = source(7L, "Verified Source");
        current.setAiEvidenceStatus("verified");
        current.setNotes("old evidence note");
        when(sourceMapper.selectByIdForUpdate(7L)).thenReturn(current);
        when(sourceMapper.selectById(7L)).thenReturn(current);
        when(sourceMapper.selectOne(any())).thenReturn(null);
        when(sourceMapper.updateById(current)).thenReturn(1);
        SourceUpdateDTO dto = updateDto("Verified Source");
        dto.setNotes("new evidence note");

        service.updateSource(7L, dto, admin());

        verify(evidenceReviewService).invalidateSourceAfterEvidenceEdit(current, admin());
    }

    @Test
    void ordinarySourceUpdateReportsConflictWhenNoRowIsUpdated() {
        Source current = source(7L, "Source");
        current.setEvidenceRevision(4L);
        current.setUpdatedAt(java.time.LocalDateTime.of(2026, 7, 25, 2, 0));
        when(sourceMapper.selectByIdForUpdate(7L)).thenReturn(current);
        when(sourceMapper.selectOne(any())).thenReturn(null);
        when(sourceMapper.updateById(any(Source.class))).thenReturn(0);
        SourceUpdateDTO dto = updateDto("Source updated");
        dto.setExpectedEvidenceRevision(4L);
        dto.setExpectedUpdatedAt(java.time.LocalDateTime.of(2026, 7, 25, 2, 0));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateSource(7L, dto, admin())
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }

    @Test
    void ordinarySourceDeleteReportsConflictWhenNoRowIsDeleted() {
        Source current = source(7L, "Source");
        current.setEvidenceRevision(0L);
        current.setUpdatedAt(java.time.LocalDateTime.of(2026, 7, 25, 2, 0));
        when(sourceMapper.selectByIdForUpdate(7L)).thenReturn(current);
        when(sourceMapper.deleteById(7L)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteSource(7L, 0L, java.time.LocalDateTime.of(2026, 7, 25, 2, 0)));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }

    private SourceCreateDTO createDto(String title) {
        SourceCreateDTO dto = new SourceCreateDTO();
        dto.setTitle(title);
        dto.setSourceType("other");
        dto.setAccessedAt(LocalDate.of(2026, 7, 23));
        dto.setStatus("pending");
        return dto;
    }

    private SourceUpdateDTO updateDto(String title) {
        SourceUpdateDTO dto = new SourceUpdateDTO();
        dto.setTitle(title);
        dto.setSourceType("web");
        dto.setAccessedAt(LocalDate.of(2026, 7, 23));
        dto.setStatus("published");
        dto.setExpectedEvidenceRevision(0L);
        dto.setExpectedUpdatedAt(java.time.LocalDateTime.of(2026, 7, 25, 1, 0));
        return dto;
    }

    private Source source(Long id, String title) {
        Source source = new Source();
        source.setId(id);
        source.setTitle(title);
        source.setSourceType("web");
        source.setAccessedAt(LocalDate.of(2026, 7, 23));
        source.setStatus("published");
        source.setEvidenceRevision(0L);
        source.setUpdatedAt(java.time.LocalDateTime.of(2026, 7, 25, 1, 0));
        return source;
    }

    private AuthenticatedAdmin admin() {
        return new AuthenticatedAdmin(7L, "reviewer");
    }

    private boolean hasParameter(Wrapper<Source> wrapper, String value) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper)) {
            return false;
        }
        abstractWrapper.getSqlSegment();
        return abstractWrapper.getParamNameValuePairs().containsValue(value);
    }
}
