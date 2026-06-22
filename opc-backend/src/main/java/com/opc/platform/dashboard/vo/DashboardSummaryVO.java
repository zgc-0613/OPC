package com.opc.platform.dashboard.vo;

import lombok.Data;

import java.util.List;

@Data
public class DashboardSummaryVO {

    private Long policyCount;

    private Long caseCount;

    private Long sourceCount;

    private Integer coveredRegionCount;

    private List<RecentUpdateVO> recentUpdates;
}
