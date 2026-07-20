package com.opc.platform.visit.controller;

import com.opc.platform.visit.mapper.VisitLogMapper;
import com.opc.platform.visit.service.VisitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VisitControllerTest {

    private MockMvc mockMvc;

    @Mock
    private VisitLogMapper visitLogMapper;

    @BeforeEach
    void setUp() {
        VisitService visitService = new VisitService(visitLogMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(new VisitController(visitService)).build();
    }

    @Test
    void trendDefaultsToSevenDays() throws Exception {
        when(visitLogMapper.selectTrend(7)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/public/visits/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        verify(visitLogMapper).selectTrend(7);
    }

    @Test
    void trendAcceptsSixMonthsAndCapsLongerRanges() throws Exception {
        when(visitLogMapper.selectTrend(180)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/public/visits/trend").param("days", "365"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(visitLogMapper).selectTrend(180);
    }
}
