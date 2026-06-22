package com.opc.platform.caseitem.controller;

import com.opc.platform.caseitem.dto.CaseItemQueryDTO;
import com.opc.platform.caseitem.service.CaseItemService;
import com.opc.platform.caseitem.vo.CaseItemDetailVO;
import com.opc.platform.caseitem.vo.CaseItemListVO;
import com.opc.platform.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/cases")
public class CaseItemController {

    private final CaseItemService caseItemService;

    @GetMapping
    public Result<List<CaseItemListVO>> listCaseItems(@ModelAttribute CaseItemQueryDTO query) {
        return Result.success(caseItemService.listCaseItems(query));
    }

    @GetMapping("/{id}")
    public Result<CaseItemDetailVO> getCaseItemDetail(@PathVariable Long id) {
        return Result.success(caseItemService.getCaseItemDetail(id));
    }
}
