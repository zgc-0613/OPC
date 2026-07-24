package com.opc.platform.ai.controller;

import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.EvidenceReviewBatchUpdateDTO;
import com.opc.platform.ai.dto.EvidenceReviewQueryDTO;
import com.opc.platform.ai.dto.EvidenceReviewUpdateDTO;
import com.opc.platform.ai.service.EvidenceReviewService;
import com.opc.platform.ai.vo.EvidenceReviewItemVO;
import com.opc.platform.ai.vo.EvidenceReviewBatchResultVO;
import com.opc.platform.ai.vo.EvidenceReviewDetailVO;
import com.opc.platform.ai.vo.EvidenceReviewPageVO;
import com.opc.platform.ai.vo.EvidenceReviewPreflightVO;
import com.opc.platform.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.opc.platform.adminauth.AdminAuthInterceptor.AUTHENTICATED_ADMIN_ATTRIBUTE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/evidence-reviews")
public class AdminEvidenceReviewController {

    private final EvidenceReviewService evidenceReviewService;

    @GetMapping
    public Result<EvidenceReviewPageVO> list(@Valid @ModelAttribute EvidenceReviewQueryDTO query) {
        return Result.success(evidenceReviewService.list(query));
    }

    @GetMapping("/{itemType}/{itemId}")
    public Result<EvidenceReviewDetailVO> detail(
            @PathVariable String itemType,
            @PathVariable Long itemId
    ) {
        return Result.success(evidenceReviewService.detail(itemType, itemId));
    }

    @PostMapping("/batch/preflight")
    public Result<EvidenceReviewPreflightVO> preflight(
            @Valid @RequestBody EvidenceReviewBatchUpdateDTO dto
    ) {
        return Result.success(evidenceReviewService.preflight(dto));
    }

    @PutMapping("/batch")
    public Result<EvidenceReviewBatchResultVO> reviewBatch(
            @Valid @RequestBody EvidenceReviewBatchUpdateDTO dto,
            @RequestAttribute(AUTHENTICATED_ADMIN_ATTRIBUTE) AuthenticatedAdmin admin
    ) {
        return Result.success(evidenceReviewService.reviewBatch(dto, admin));
    }

    @PutMapping("/{itemType}/{itemId}")
    public Result<EvidenceReviewItemVO> review(
            @PathVariable String itemType,
            @PathVariable Long itemId,
            @Valid @RequestBody EvidenceReviewUpdateDTO dto,
            @RequestAttribute(AUTHENTICATED_ADMIN_ATTRIBUTE) AuthenticatedAdmin admin
    ) {
        return Result.success(evidenceReviewService.review(itemType, itemId, dto, admin));
    }
}
