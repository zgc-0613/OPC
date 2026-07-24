package com.opc.platform.caseitem.service;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.opc.platform.caseitem.dto.CaseItemQueryDTO;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.mapper.SourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

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
                org.mockito.Mockito.mock(com.opc.platform.casetag.mapper.CaseTagMapper.class)
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

    private boolean hasParameter(Wrapper<CaseItem> wrapper, String value) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper)) {
            return false;
        }
        abstractWrapper.getSqlSegment();
        return abstractWrapper.getParamNameValuePairs().containsValue(value);
    }
}
