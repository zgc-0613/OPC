package com.opc.platform.export.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.mapper.SourceMapper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelExportServiceTest {

    @BeforeAll
    static void initializePolicyTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Policy.class
        );
    }

    @Mock
    private SourceMapper sourceMapper;

    @Mock
    private PolicyMapper policyMapper;

    @Mock
    private CaseItemMapper caseItemMapper;

    @Mock
    private RegionMapper regionMapper;

    @InjectMocks
    private ExcelExportService excelExportService;

    @Test
    void publicPolicyExportContainsPublishedRecordsOnly() throws Exception {
        Policy published = new Policy();
        published.setId(9L);
        published.setTitle("Published policy");
        published.setRegionId(1L);
        published.setSourceId(2L);
        published.setStatus("published");

        when(policyMapper.selectList(argThat(this::hasPublishedFilter))).thenReturn(List.of(published));
        when(regionMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        when(sourceMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        MockHttpServletResponse response = new MockHttpServletResponse();
        excelExportService.exportPublishedPolicies(response);

        assertTrue(response.getContentType().startsWith(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(response.getContentAsByteArray())
        )) {
            assertEquals(1, workbook.getSheet("policies").getLastRowNum());
            assertEquals("Published policy", workbook.getSheet("policies").getRow(1).getCell(1).getStringCellValue());
        }
    }

    private boolean hasPublishedFilter(Wrapper<Policy> wrapper) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper)) {
            return false;
        }
        abstractWrapper.getSqlSegment();
        return abstractWrapper.getParamNameValuePairs().containsValue("published");
    }
}
