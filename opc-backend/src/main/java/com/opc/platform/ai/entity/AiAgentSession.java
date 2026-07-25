package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_agent_sessions")
public class AiAgentSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String status;
    private String profileJson;
    private String researchContextJson;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;
}
