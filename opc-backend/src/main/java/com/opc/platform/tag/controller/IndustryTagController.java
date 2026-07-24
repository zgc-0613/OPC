package com.opc.platform.tag.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.tag.service.IndustryTagService;
import com.opc.platform.tag.vo.IndustryTagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/industry-tags")
public class IndustryTagController {

    private final IndustryTagService industryTagService;

    @GetMapping
    public Result<List<IndustryTagVO>> list() {
        return Result.success(industryTagService.listIndustries());
    }
}
