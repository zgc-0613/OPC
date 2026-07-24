package com.opc.platform.ai.vo;

import lombok.Data;

import java.util.List;

@Data
public class EvidenceReviewPreflightItemVO {
    private String itemType;
    private Long itemId;
    private String title;
    private boolean allowed;
    private List<String> blockingReasons;
    private int affectedCaseCount;
    private int affectedPolicyCount;
}
