package com.opc.platform.dashboard.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.dashboard.service.DashboardService;
import com.opc.platform.dashboard.vo.DashboardSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public Result<DashboardSummaryVO> getSummary() {
        return Result.success(dashboardService.getSummary());
    }
}
