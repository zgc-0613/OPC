package com.opc.platform.region.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.region.service.RegionService;
import com.opc.platform.region.vo.RegionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/regions")
public class RegionController {

    private final RegionService regionService;

    @GetMapping
    public Result<List<RegionVO>> listRegions() {
        return Result.success(regionService.listRegions());
    }
}