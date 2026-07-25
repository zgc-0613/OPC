package com.opc.platform.caseitem.controller;

import com.opc.platform.caseitem.dto.CaseItemCreateDTO;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.caseitem.dto.CaseItemQueryDTO;
import com.opc.platform.caseitem.dto.CaseItemUpdateDTO;
import com.opc.platform.caseitem.service.CaseItemService;
import com.opc.platform.caseitem.vo.CaseItemDetailVO;
import com.opc.platform.caseitem.vo.CaseItemListVO;
import com.opc.platform.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

import static com.opc.platform.adminauth.AdminAuthInterceptor.AUTHENTICATED_ADMIN_ATTRIBUTE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/cases")
public class CaseItemAdminController {

    private final CaseItemService caseItemService;

    @GetMapping
    public Result<List<CaseItemListVO>> listCaseItems(@ModelAttribute CaseItemQueryDTO query) {
        return Result.success(caseItemService.listCaseItems(query));
    }

    @GetMapping("/{id}")
    public Result<CaseItemDetailVO> getCaseItemDetail(@PathVariable Long id) {
        return Result.success(caseItemService.getCaseItemDetail(id));
    }

    @PostMapping
    public Result<CaseItemDetailVO> createCaseItem(@Valid @RequestBody CaseItemCreateDTO dto) {
        return Result.success(caseItemService.createCaseItem(dto));
    }

    @PutMapping("/{id}")
    public Result<CaseItemDetailVO> updateCaseItem(
            @PathVariable Long id,
            @Valid @RequestBody CaseItemUpdateDTO dto,
            @RequestAttribute(AUTHENTICATED_ADMIN_ATTRIBUTE) AuthenticatedAdmin admin
    ) {
        return Result.success(caseItemService.updateCaseItem(id, dto, admin));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteCaseItem(
            @PathVariable Long id,
            @RequestParam Long expectedEvidenceRevision,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expectedUpdatedAt
    ) {
        caseItemService.deleteCaseItem(id, expectedEvidenceRevision, expectedUpdatedAt);
        return Result.success();
    }
}
