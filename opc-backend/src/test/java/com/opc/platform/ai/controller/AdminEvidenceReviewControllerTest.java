package com.opc.platform.ai.controller;

import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.EvidenceReviewQueryDTO;
import com.opc.platform.ai.service.EvidenceReviewService;
import com.opc.platform.ai.vo.EvidenceReviewBatchResultVO;
import com.opc.platform.ai.vo.EvidenceReviewDetailVO;
import com.opc.platform.ai.vo.EvidenceReviewPageVO;
import com.opc.platform.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.opc.platform.adminauth.AdminAuthInterceptor.AUTHENTICATED_ADMIN_ATTRIBUTE;

@ExtendWith(MockitoExtension.class)
class AdminEvidenceReviewControllerTest {

    @Mock
    private EvidenceReviewService evidenceReviewService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminEvidenceReviewController(evidenceReviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void blankFiltersMeanAllEvidenceInsteadOfFailingValidation() throws Exception {
        when(evidenceReviewService.list(any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/admin/evidence-reviews")
                        .param("itemType", "")
                        .param("evidenceStatus", "")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<EvidenceReviewQueryDTO> query = ArgumentCaptor.forClass(EvidenceReviewQueryDTO.class);
        verify(evidenceReviewService).list(query.capture());
        assertEquals("", query.getValue().getItemType());
        assertEquals("", query.getValue().getEvidenceStatus());
    }

    @Test
    void unsupportedNonBlankFiltersRemainRejected() throws Exception {
        mockMvc.perform(get("/api/admin/evidence-reviews")
                        .param("itemType", "report")
                        .param("evidenceStatus", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void authenticatedAdministratorCanBatchReviewSelectedEvidence() throws Exception {
        EvidenceReviewBatchResultVO result = new EvidenceReviewBatchResultVO();
        result.setProcessedCount(2);
        result.setItems(List.of());
        when(evidenceReviewService.reviewBatch(any(), any())).thenReturn(result);

        mockMvc.perform(put("/api/admin/evidence-reviews/batch")
                        .requestAttr(AUTHENTICATED_ADMIN_ATTRIBUTE, new AuthenticatedAdmin(7L, "reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"itemType": "case", "itemId": 11, "expectedEvidenceStatus": "legacy_unverified", "expectedUpdatedAt": "2026-07-25T00:00:00", "expectedVersion": 0},
                                    {"itemType": "source", "itemId": 8, "expectedEvidenceStatus": "legacy_unverified", "expectedUpdatedAt": "2026-07-25T00:00:00", "expectedVersion": 0}
                                  ],
                                  "evidenceStatus": "excluded"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.processedCount").value(2));
    }

    @Test
    void singleReviewRejectsMissingOptimisticConcurrencySnapshot() throws Exception {
        mockMvc.perform(put("/api/admin/evidence-reviews/source/8")
                        .requestAttr(AUTHENTICATED_ADMIN_ATTRIBUTE, new AuthenticatedAdmin(7L, "reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"evidenceStatus": "verified"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void detailRouteLoadsOneEvidenceReviewWorkspaceItem() throws Exception {
        EvidenceReviewDetailVO detail = new EvidenceReviewDetailVO();
        detail.setItemType("source");
        detail.setItemId(8L);
        when(evidenceReviewService.detail("source", 8L)).thenReturn(detail);

        mockMvc.perform(get("/api/admin/evidence-reviews/source/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.itemType").value("source"))
                .andExpect(jsonPath("$.data.itemId").value(8));
    }

    private EvidenceReviewPageVO emptyPage() {
        EvidenceReviewPageVO page = new EvidenceReviewPageVO();
        page.setItems(List.of());
        page.setPage(1);
        page.setSize(20);
        page.setTotal(0);
        return page;
    }
}
