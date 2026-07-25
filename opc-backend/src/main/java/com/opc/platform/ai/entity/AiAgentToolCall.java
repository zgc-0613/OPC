package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_tool_calls")
public class AiAgentToolCall {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long analysisRunId;
    private Integer stepNo;
    private String toolName;
    private String argumentsJson;
    private String resultSummaryJson;
    private String evidenceHash;
    private String status;
    private String diagnosticCode;
    private Integer evidenceCount;
    private Long latencyMs;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
