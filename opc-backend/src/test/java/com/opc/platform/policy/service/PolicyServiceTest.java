package com.opc.platform.policy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.opc.platform.policy.dto.PolicyQueryDTO;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.tag.mapper.TagMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "policy-test"),
                Policy.class
        );
    }

    @Mock
    private PolicyMapper policyMapper;

    @Mock
    private RegionMapper regionMapper;

    @Mock
    private SourceMapper sourceMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private PolicyTagMapper policyTagMapper;

    private PolicyService service;

    @BeforeEach
    void setUp() {
        service = new PolicyService(policyMapper, regionMapper, sourceMapper, tagMapper, policyTagMapper);
    }

    @Test
    void publicListAlwaysReadsPublishedPolicies() {
        PolicyQueryDTO query = new PolicyQueryDTO();
        query.setStatus("draft");
        Policy published = new Policy();
        published.setId(3L);
        published.setRegionId(1L);
        published.setSourceId(2L);
        published.setStatus("published");

        when(policyMapper.selectList(argThat(wrapper -> hasParameter(wrapper, "published"))))
                .thenReturn(List.of(published));
        when(regionMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        when(sourceMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        var result = service.listPublicPolicies(query);

        assertEquals(1, result.size());
        assertEquals("published", result.get(0).getStatus());
    }

    @Test
    void publicDetailHidesDraftPolicies() {
        Policy draft = new Policy();
        draft.setId(11L);
        draft.setStatus("draft");
        when(policyMapper.selectById(11L)).thenReturn(draft);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getPublicPolicyDetail(11L)
        );

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    private boolean hasParameter(Wrapper<Policy> wrapper, String value) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper)) {
            return false;
        }
        abstractWrapper.getSqlSegment();
        return abstractWrapper.getParamNameValuePairs().containsValue(value);
    }
}
