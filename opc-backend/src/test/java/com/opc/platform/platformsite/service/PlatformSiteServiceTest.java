package com.opc.platform.platformsite.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.opc.platform.platformsite.entity.PlatformSite;
import com.opc.platform.platformsite.mapper.PlatformSiteMapper;
import com.opc.platform.platformsite.vo.PlatformSiteVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSiteServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "platform-site-test"),
                PlatformSite.class
        );
    }

    @Mock
    private PlatformSiteMapper mapper;

    @Test
    void publicListQueriesPublishedRowsAndMapsWebsiteFields() {
        PlatformSite site = new PlatformSite();
        site.setId(7L);
        site.setName("示例平台");
        site.setUrl("https://example.com/");
        site.setCategory("地方独立一站式平台");
        site.setOwner("示例主体");
        site.setServices("创业服务");
        site.setSummary("示例简介");
        site.setDomain("example.com");
        site.setFaviconUrl("https://example.com/favicon.ico");
        site.setSortOrder(7);
        site.setVerificationStatus("pending");
        when(mapper.selectList(any())).thenReturn(List.of(site));

        List<PlatformSiteVO> result = new PlatformSiteService(mapper).listPublishedSites();

        assertEquals(1, result.size());
        assertEquals("示例平台", result.get(0).getName());
        assertEquals("https://example.com/", result.get(0).getUrl());
        assertEquals("https://example.com/favicon.ico", result.get(0).getLogoUrl());
        assertEquals("pending", result.get(0).getVerificationStatus());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<PlatformSite>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("status"));
        assertEquals("published", query.getValue().getParamNameValuePairs().get("MPGENVAL1"));
        assertTrue(query.getValue().getSqlSegment().contains("sort_order"));
    }
}
