package com.opc.platform.dashboard.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "dashboard-policy-test"), Policy.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "dashboard-case-test"), CaseItem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "dashboard-source-test"), Source.class);
    }

    @Mock
    private PolicyMapper policyMapper;

    @Mock
    private CaseItemMapper caseItemMapper;

    @Mock
    private SourceMapper sourceMapper;

    @Mock
    private RegionMapper regionMapper;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(policyMapper, caseItemMapper, sourceMapper, regionMapper);
    }

    @Test
    void summaryCountsOnlyPublishedContent() {
        when(policyMapper.selectCount(argThat(this::hasPublishedParameter))).thenReturn(12L);
        when(caseItemMapper.selectCount(argThat(this::hasPublishedParameter))).thenReturn(23L);
        when(sourceMapper.selectCount(argThat(this::hasPublishedParameter))).thenReturn(34L);

        var summary = service.getSummary();

        assertEquals(12L, summary.getPolicyCount());
        assertEquals(23L, summary.getCaseCount());
        assertEquals(34L, summary.getSourceCount());
    }

    @Test
    void coveredRegionsOnlyUsePublishedPoliciesAndCases() {
        when(policyMapper.selectObjs(argThat(this::hasPublishedParameter)))
                .thenReturn(List.of(1L));
        when(caseItemMapper.selectObjs(argThat(this::hasPublishedParameter)))
                .thenReturn(List.of(2L));

        var summary = service.getSummary();

        assertEquals(2, summary.getCoveredRegionCount());
    }

    @Test
    void recentUpdatesOnlyUsePublishedContent() {
        Policy published = new Policy();
        published.setId(14L);
        published.setRegionId(1L);
        published.setStatus("published");
        published.setAccessedAt(LocalDate.of(2026, 7, 23));
        when(policyMapper.selectList(argThat(this::hasPublishedParameter)))
                .thenReturn(List.of(published));
        when(caseItemMapper.selectList(argThat(this::hasPublishedParameter)))
                .thenReturn(Collections.emptyList());
        when(sourceMapper.selectList(argThat(this::hasPublishedParameter)))
                .thenReturn(Collections.emptyList());
        when(regionMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        var summary = service.getSummary();

        assertEquals(1, summary.getRecentUpdates().size());
        assertEquals("published", summary.getRecentUpdates().get(0).getStatus());
    }

    private boolean hasPublishedParameter(Wrapper<?> wrapper) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper)) {
            return false;
        }
        abstractWrapper.getSqlSegment();
        return abstractWrapper.getParamNameValuePairs().containsValue("published");
    }
}
