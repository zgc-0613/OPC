package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_run_feedback")
public class AgentRunFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long runId;
    private String rating;
    private String reason;
    @TableField("comment_text")
    private String comment;
    private Long revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
