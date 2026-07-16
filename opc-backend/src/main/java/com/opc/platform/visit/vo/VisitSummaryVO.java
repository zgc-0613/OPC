package com.opc.platform.visit.vo;

import lombok.Data;

@Data
public class VisitSummaryVO {
    private Long totalPv;

    private Long totalUv;

    private Long todayPv;

    private Long todayUv;

    private Long policyPv;

    private Long casePv;
    
}
