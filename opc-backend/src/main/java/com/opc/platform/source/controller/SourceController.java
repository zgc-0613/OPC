package com.opc.platform.source.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.source.service.SourceService;
import com.opc.platform.source.vo.SourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/sources")
public class SourceController {

    private final SourceService sourceService;

    @GetMapping
    public Result<List<SourceVO>> listSources() {
        return Result.success(sourceService.listPublicSources());
    }
}
