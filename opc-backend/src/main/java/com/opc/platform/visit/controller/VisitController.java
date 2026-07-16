package com.opc.platform.visit.controller;


import com.opc.platform.common.result.Result;
import com.opc.platform.visit.dto.VisitCreateDTO;
import com.opc.platform.visit.service.VisitService;
import com.opc.platform.visit.vo.VisitSummaryVO;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.opc.platform.visit.vo.VisitRankingVO;
import com.opc.platform.visit.vo.VisitTrendVO;

import java.util.List;

@RestController
@RequestMapping("/api/public/visits")
@RequiredArgsConstructor
public class VisitController {
    private final VisitService visitService;

    @PostMapping
    public Result<Void> recordVisit(@RequestBody VisitCreateDTO dto, HttpServletRequest request) {
        visitService.recordVisit(dto, request);
        return Result.success();
    }

    @GetMapping("/summary")
    public Result<VisitSummaryVO> getSummary() {
        return Result.success(visitService.getSummary());
    }

    @GetMapping("/rankings")
    public Result<List<VisitRankingVO>> getRankings(
            @RequestParam(defaultValue = "policy") String targetType,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return Result.success(visitService.getRankings(targetType, limit));
    }

    @GetMapping("/trend")
    public Result<List<VisitTrendVO>> getTrend(
            @RequestParam(defaultValue = "7") Integer days
    ) {
        return Result.success(visitService.getTrend(days));
    }

}
