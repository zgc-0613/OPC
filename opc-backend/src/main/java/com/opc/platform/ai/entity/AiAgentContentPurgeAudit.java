package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_content_purge_audits")
public class AiAgentContentPurgeAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String operation;
    private Long sessionId;
    private Long userId;
    private String operatorType;
    private Long operatorId;
    private String result;
    private String diagnosticCode;
    private LocalDateTime createdAt;
}
