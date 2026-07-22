package com.opc.platform.source.service;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.source.dto.SourceCreateDTO;
import com.opc.platform.source.dto.SourceUpdateDTO;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private SourceMapper sourceMapper;

    private SourceService service;

    @BeforeEach
    void setUp() {
        service = new SourceService(sourceMapper);
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
        when(sourceMapper.selectById(7L)).thenReturn(current);
        when(sourceMapper.selectOne(any())).thenReturn(source(42L, "Policy Daily"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateSource(7L, updateDto("Policy Daily"))
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Policy Daily"));
        assertTrue(exception.getMessage().contains("42"));
        verify(sourceMapper, never()).updateById(any(Source.class));
    }

    @Test
    void renameWithoutConflictStoresTheTrimmedTitle() {
        Source current = source(7L, "Old Source");
        when(sourceMapper.selectById(7L)).thenReturn(current, current);
        when(sourceMapper.selectOne(any())).thenReturn(null);

        service.updateSource(7L, updateDto("  Renamed Source  "));

        ArgumentCaptor<Source> captor = ArgumentCaptor.forClass(Source.class);
        verify(sourceMapper).updateById(captor.capture());
        assertEquals("Renamed Source", captor.getValue().getTitle());
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
        dto.setStatus("active");
        return dto;
    }

    private Source source(Long id, String title) {
        Source source = new Source();
        source.setId(id);
        source.setTitle(title);
        source.setSourceType("web");
        source.setAccessedAt(LocalDate.of(2026, 7, 23));
        source.setStatus("active");
        return source;
    }
}
