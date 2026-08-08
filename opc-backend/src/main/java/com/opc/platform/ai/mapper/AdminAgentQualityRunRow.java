package com.opc.platform.ai.mapper;

import lombok.Data;

@Data
public class AdminAgentQualityRunRow {
    private String taskType;
    private String model;
    private String promptVersion;
    private String status;
    private String diagnosticCode;
    private Long runCount;
    private Long totalTokens;
    private Long latencyMs;
    private Long toolCallCount;
}
