package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_sessions")
public class AiAgentSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String titleMode;
    private String status;
    private String profileJson;
    private String researchContextJson;
    private String taskContextVersion;
    private String taskContextJson;
    private String taskContextHash;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime pinnedAt;
    private LocalDateTime archivedAt;
    private LocalDateTime deletedAt;
    private LocalDateTime purgeAfter;
    private LocalDateTime purgedAt;
    private Long contentGeneration;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;

    @TableField(exist = false)
    private String activeRunStatus;

    @TableField(exist = false)
    private LocalDateTime historyActivity;

    @TableField(exist = false)
    private Integer historyPinned;
}
