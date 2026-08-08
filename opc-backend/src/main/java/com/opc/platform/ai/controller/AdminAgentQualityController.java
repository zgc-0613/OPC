package com.opc.platform.ai.controller;

import com.opc.platform.ai.service.AdminAgentQualityService;
import com.opc.platform.ai.vo.AdminAgentQualityVO;
import com.opc.platform.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/ai/research")
public class AdminAgentQualityController {

    private final AdminAgentQualityService quality;

    @GetMapping("/quality")
    public Result<AdminAgentQualityVO> quality(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String promptVersion,
            @RequestParam(defaultValue = "day") String granularity
    ) {
        return Result.success(quality.quality(dateFrom, dateTo, taskType, model, promptVersion, granularity));
    }
}
