package com.opc.platform.source.controller;

import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.source.service.SourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SourceAdminControllerTest {

    @Mock
    private SourceMapper sourceMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SourceService service = new SourceService(sourceMapper,
                org.mockito.Mockito.mock(com.opc.platform.ai.service.EvidenceReviewService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new SourceAdminController(service)).build();
    }

    @Test
    void adminListCanReadDraftSources() throws Exception {
        Source draft = new Source();
        draft.setId(5L);
        draft.setStatus("draft");
        when(sourceMapper.selectList(any())).thenReturn(List.of(draft));

        mockMvc.perform(get("/api/admin/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("draft"));
    }
}
