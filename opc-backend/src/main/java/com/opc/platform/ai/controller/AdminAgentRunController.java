package com.opc.platform.ai.controller;

import com.opc.platform.ai.service.AdminAgentRunService;
import com.opc.platform.ai.vo.AdminAgentRunDetailVO;
import com.opc.platform.ai.vo.AdminAgentRunRowVO;
import com.opc.platform.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/ai-agent-runs")
public class AdminAgentRunController {

    private final AdminAgentRunService service;

    @GetMapping
    public Result<List<AdminAgentRunRowVO>> list(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return Result.success(service.list(limit));
    }

    @GetMapping("/{runId}")
    public Result<AdminAgentRunDetailVO> detail(@PathVariable Long runId) {
        return Result.success(service.detail(runId));
    }
}
