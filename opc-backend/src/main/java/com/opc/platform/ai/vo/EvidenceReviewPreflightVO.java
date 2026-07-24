package com.opc.platform.ai.vo;

import lombok.Data;

import java.util.List;

@Data
public class EvidenceReviewPreflightVO {
    private int requestedCount;
    private int actionableCount;
    private int blockedCount;
    private int affectedCaseCount;
    private int affectedPolicyCount;
    private List<EvidenceReviewPreflightItemVO> items;
}
