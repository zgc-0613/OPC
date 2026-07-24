package com.opc.platform.caseitem.controller;

import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.caseitem.service.CaseItemService;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.mapper.SourceMapper;
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
class CaseItemAdminControllerTest {

    @Mock
    private CaseItemMapper caseItemMapper;

    @Mock
    private RegionMapper regionMapper;

    @Mock
    private SourceMapper sourceMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CaseItemService service = new CaseItemService(
                caseItemMapper,
                regionMapper,
                sourceMapper,
                org.mockito.Mockito.mock(com.opc.platform.tag.mapper.TagMapper.class),
                org.mockito.Mockito.mock(com.opc.platform.casetag.mapper.CaseTagMapper.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new CaseItemAdminController(service)).build();
    }

    @Test
    void adminListCanReadDraftCases() throws Exception {
        CaseItem draft = new CaseItem();
        draft.setId(4L);
        draft.setRegionId(1L);
        draft.setSourceId(2L);
        draft.setStatus("draft");
        when(caseItemMapper.selectList(any())).thenReturn(List.of(draft));
        when(regionMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        when(sourceMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/cases").param("status", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value("draft"));
    }
}
