package com.opc.platform.ai.tool;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentPolicySearchRow {
    private Long policyId;
    private String title;
    private String policyType;
    private String summary;
    private String supportMeasures;
    private String applicabilityMode;
    private String geographicLevel;
    private Long regionId;
    private Long sourceId;
    private Long policyRevision;
    private Long sourceRevision;
    private LocalDateTime policyUpdatedAt;
    private LocalDateTime sourceUpdatedAt;
}
