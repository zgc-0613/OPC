package com.opc.platform.platformsite.controller;

import com.opc.platform.platformsite.service.PlatformSiteService;
import com.opc.platform.platformsite.vo.PlatformSiteVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlatformSiteControllerTest {

    @Mock
    private PlatformSiteService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlatformSiteController(service)).build();
    }

    @Test
    void exposesPublishedPlatformDirectory() throws Exception {
        PlatformSiteVO site = new PlatformSiteVO();
        site.setName("示例平台");
        site.setUrl("https://example.com/");
        site.setCategory("地方独立一站式平台");
        site.setLogoUrl("https://example.com/favicon.ico");
        when(service.listPublishedSites()).thenReturn(List.of(site));

        mockMvc.perform(get("/api/public/platform-sites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("示例平台"))
                .andExpect(jsonPath("$.data[0].logoUrl").value("https://example.com/favicon.ico"))
                .andExpect(jsonPath("$.data[0].category").value("地方独立一站式平台"));
    }
}
