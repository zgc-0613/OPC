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
    private Long sessionId;
    private Long userMessageId;
    private String idempotencyKey;
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
    private String leaseOwner;
    private LocalDateTime leaseExpiresAt;
    private Integer executionAttempts;
    private LocalDateTime nextAttemptAt;
    private String lastRecoveryReason;
    private String settlementStatus;
    private LocalDateTime providerDispatchedAt;
    private LocalDateTime settledAt;
    private Long settlementVersion;
    private Long latencyMs;
    private String providerRequestId;
    private String finishReason;
    private String responseHash;
    private String errorType;
    private String diagnosticCode;
    private Integer stepCount;
    private Integer toolCallCount;
    private String currentStage;
    private String visibleProgress;
    private LocalDateTime cancelledAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
