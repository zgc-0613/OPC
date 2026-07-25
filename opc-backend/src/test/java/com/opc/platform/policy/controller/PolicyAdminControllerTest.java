package com.opc.platform.policy.controller;

import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policy.service.PolicyService;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
import com.opc.platform.policyindustrytag.mapper.PolicyIndustryTagMapper;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.tag.mapper.TagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PolicyAdminControllerTest {

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PolicyService service = new PolicyService(policyMapper, regionMapper, sourceMapper, tagMapper, policyTagMapper,
                org.mockito.Mockito.mock(PolicyIndustryTagMapper.class),
                org.mockito.Mockito.mock(com.opc.platform.ai.service.EvidenceReviewService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new PolicyAdminController(service)).build();
    }

    @Test
    void adminListCanReadDraftPolicies() throws Exception {
        Policy draft = new Policy();
        draft.setId(6L);
        draft.setRegionId(1L);
        draft.setSourceId(2L);
        draft.setStatus("draft");
        when(policyMapper.selectList(any())).thenReturn(List.of(draft));
        when(regionMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        when(sourceMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/policies").param("status", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("draft"));
    }
}
