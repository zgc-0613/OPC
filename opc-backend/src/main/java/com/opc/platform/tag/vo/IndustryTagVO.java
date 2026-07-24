package com.opc.platform.tag.vo;

public record IndustryTagVO(
        Long tagId,
        String name,
        String type,
        long caseUsageCount,
        long policyUsageCount
) {
}
