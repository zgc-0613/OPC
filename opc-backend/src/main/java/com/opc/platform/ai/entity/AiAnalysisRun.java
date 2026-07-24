package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_analysis_runs")
public class AiAnalysisRun {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String taskType;
    private Long caseId;
    private String status;
    private String resultJson;
    private String provider;
    private String modelId;
    private String promptVersion;
    private String evidenceHash;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long reservedTokens;
    private LocalDateTime startedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime heartbeatAt;
    private Long latencyMs;
    private String providerRequestId;
    private String errorType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
