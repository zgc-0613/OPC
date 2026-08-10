package com.opc.platform.ai.controller;

import com.opc.platform.ai.service.AnalyticsOverviewService;
import com.opc.platform.ai.vo.AnalyticsIndustriesVO;
import com.opc.platform.ai.vo.AnalyticsOverviewVO;
import com.opc.platform.ai.vo.AnalyticsUnavailableVO;
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

    @GetMapping("/technologies")
    public Result<AnalyticsUnavailableVO> technologies(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "technology.case_count") String metricId,
            @RequestParam(required = false) java.util.List<Long> technologyTagIds
    ) {
        return Result.success(service.technologies(user, metricId, technologyTagIds));
    }

    @GetMapping("/revenue")
    public Result<AnalyticsUnavailableVO> revenue(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "revenue.range_distribution") String metricId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String revenuePeriod,
            @RequestParam(required = false) String revenueType
    ) {
        return Result.success(service.revenue(
                user, metricId, currency, revenuePeriod, revenueType));
    }

    @GetMapping("/regions")
    public Result<AnalyticsUnavailableVO> regions(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "region.case_count") String metricId,
            @RequestParam(required = false) String regionRole
    ) {
        return Result.success(service.regions(user, metricId, regionRole));
    }

    @GetMapping("/trends")
    public Result<AnalyticsUnavailableVO> trends(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "trend.policy_publish_time") String metricId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) String regionRole,
            @RequestParam(required = false) java.util.List<Long> industryTagIds,
            @RequestParam(required = false) java.util.List<Long> technologyTagIds
    ) {
        return Result.success(service.trends(
                user, metricId, dateFrom, dateTo, granularity, regionId, regionRole,
                industryTagIds, technologyTagIds));
    }

    @GetMapping("/drilldown")
    public Result<AnalyticsUnavailableVO> drilldown(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam String metricId,
            @RequestParam String dataVersion,
            @RequestParam String entityType,
            @RequestParam(required = false) String bucketId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return Result.success(service.drilldown(
                user, metricId, dataVersion, entityType, bucketId, cursor, limit));
    }
}
