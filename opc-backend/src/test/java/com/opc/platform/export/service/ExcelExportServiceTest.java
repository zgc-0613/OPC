package com.opc.platform.export.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.casetag.entity.CaseTag;
import com.opc.platform.casetag.mapper.CaseTagMapper;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policyindustrytag.entity.PolicyIndustryTag;
import com.opc.platform.policyindustrytag.mapper.PolicyIndustryTagMapper;
import com.opc.platform.policytag.entity.PolicyTag;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import com.opc.platform.tagalias.entity.TagAlias;
import com.opc.platform.tagalias.mapper.TagAliasMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        for (Class<?> entity : List.of(
                Policy.class, CaseItem.class, Source.class, Region.class, Tag.class,
                TagAlias.class, PolicyTag.class, PolicyIndustryTag.class, CaseTag.class
        )) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), entity.getName()),
                    entity
            );
        }
    }

    @Mock
    private SourceMapper sourceMapper;

    @Mock
    private PolicyMapper policyMapper;

    @Mock
    private CaseItemMapper caseItemMapper;

    @Mock
    private RegionMapper regionMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private TagAliasMapper tagAliasMapper;

    @Mock
    private PolicyTagMapper policyTagMapper;

    @Mock
    private PolicyIndustryTagMapper policyIndustryTagMapper;

    @Mock
    private CaseTagMapper caseTagMapper;

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

    @Test
    void paperDatasetExportsEveryRecordAndFlagsIncompleteRows() throws Exception {
        Source source = new Source();
        source.setId(2L);
        source.setTitle("Official source");
        source.setSourceType("government_site");
        source.setPublisher("Authority");
        source.setUrl("https://example.gov/policy");
        source.setAccessedAt(LocalDate.of(2026, 8, 16));
        source.setStatus("published");
        source.setAiEvidenceStatus("verified");
        source.setEvidenceRevision(3L);

        Region country = new Region();
        country.setId(1L);
        country.setName("中国");
        country.setLevel("country");
        Region province = new Region();
        province.setId(11L);
        province.setName("北京市");
        province.setLevel("province");
        province.setParentId(1L);

        Policy policy = new Policy();
        policy.setId(9L);
        policy.setTitle("Complete policy");
        policy.setRegionId(11L);
        policy.setIssuingBody("Authority");
        policy.setPublishDate(LocalDate.of(2026, 8, 1));
        policy.setSourceId(2L);
        policy.setPolicyLevel("provincial");
        policy.setPolicyType("comprehensive");
        policy.setSummary("Summary");
        policy.setKeyPoints("Key points");
        policy.setOriginalUrl("https://example.gov/policy");
        policy.setAccessedAt(LocalDate.of(2026, 8, 16));
        policy.setStatus("published");
        policy.setAiEvidenceStatus("verified");
        policy.setEvidenceRevision(2L);
        policy.setUpdatedAt(LocalDateTime.of(2026, 8, 16, 10, 0));

        CaseItem incompleteCase = new CaseItem();
        incompleteCase.setId(12L);
        incompleteCase.setTitle("Incomplete case");
        incompleteCase.setArticleTitle("Original incomplete case article");
        incompleteCase.setRegionId(11L);
        incompleteCase.setCategory("AI content");
        incompleteCase.setSubcategory("AI video");
        incompleteCase.setActorName("Founder");
        incompleteCase.setSourceId(2L);
        incompleteCase.setSummary("Summary");
        incompleteCase.setBusinessModel("Business model");
        incompleteCase.setOriginalUrl("https://example.gov/case");
        incompleteCase.setAccessedAt(LocalDate.of(2026, 8, 16));
        incompleteCase.setStatus("published");
        incompleteCase.setAiEvidenceStatus("verified");

        when(sourceMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(source));
        when(policyMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(policy));
        when(caseItemMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(incompleteCase));
        when(regionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(country, province));
        when(tagMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(tagAliasMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(policyTagMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(policyIndustryTagMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(caseTagMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        MockHttpServletResponse response = new MockHttpServletResponse();
        excelExportService.exportPaperDataset(response);

        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(response.getContentAsByteArray())
        )) {
            assertTrue(workbook.getSheet("README") != null);
            assertTrue(workbook.getSheet("sources_full") != null);
            assertTrue(workbook.getSheet("policies_full") != null);
            assertTrue(workbook.getSheet("cases_full") != null);
            assertTrue(workbook.getSheet("data_audit") != null);
            assertEquals("中国 / 北京市",
                    workbook.getSheet("policies_full").getRow(1).getCell(4).getStringCellValue());
            assertEquals("true",
                    workbook.getSheet("policies_full").getRow(1).getCell(36).getStringCellValue());
            assertTrue(workbook.getSheet("cases_full").getRow(1).getCell(30).getStringCellValue()
                    .contains("ai_tools"));
            assertEquals(3, workbook.getSheet("data_audit").getLastRowNum());
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
