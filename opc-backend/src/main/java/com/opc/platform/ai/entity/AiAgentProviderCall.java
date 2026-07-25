package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_provider_calls")
public class AiAgentProviderCall {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long analysisRunId;
    private Integer roundNo;
    private String internalRequestId;
    private String providerRequestId;
    private String settlementStatus;
    private Integer reservedTokens;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String finishReason;
    private Long latencyMs;
    private LocalDateTime dispatchedAt;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
}
