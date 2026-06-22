package com.opc.platform.caseitem.controller;

import com.opc.platform.caseitem.dto.CaseItemCreateDTO;
import com.opc.platform.caseitem.dto.CaseItemUpdateDTO;
import com.opc.platform.caseitem.service.CaseItemService;
import com.opc.platform.caseitem.vo.CaseItemDetailVO;
import com.opc.platform.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/cases")
public class CaseItemAdminController {

    private final CaseItemService caseItemService;

    @PostMapping
    public Result<CaseItemDetailVO> createCaseItem(@Valid @RequestBody CaseItemCreateDTO dto) {
        return Result.success(caseItemService.createCaseItem(dto));
    }

    @PutMapping("/{id}")
    public Result<CaseItemDetailVO> updateCaseItem(@PathVariable Long id, @Valid @RequestBody CaseItemUpdateDTO dto) {
        return Result.success(caseItemService.updateCaseItem(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteCaseItem(@PathVariable Long id) {
        caseItemService.deleteCaseItem(id);
        return Result.success();
    }
}
