package com.opc.platform.ai.controller;

import com.opc.platform.ai.service.AnalyticsOverviewService;
import com.opc.platform.ai.vo.AnalyticsIndustriesVO;
import com.opc.platform.ai.vo.AnalyticsOverviewVO;
import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.opc.platform.userauth.UserAuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsOverviewService service;

    @GetMapping("/overview")
    public Result<AnalyticsOverviewVO> overview(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
        return Result.success(service.overview(user));
    }

    @GetMapping("/industries")
    public Result<AnalyticsIndustriesVO> industries(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "industry.case_count") String metricId,
            @RequestParam(required = false) java.util.List<Long> industryTagIds
    ) {
        return Result.success(service.industries(user, metricId, industryTagIds));
    }
}
