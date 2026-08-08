package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_messages")
public class AiAgentMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String status;
    private Integer sequenceNo;
    private Long runId;
    private String citationsJson;
    @TableField(exist = false)
    private String structuredResultJson;
    @TableField(exist = false)
    private String analyticsSnapshotJson;
    private LocalDateTime createdAt;
}
