package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_research_reports")
public class AgentResearchReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long sessionId;
    private Long runId;
    private Long finalMessageId;
    private String idempotencyKey;
    private String title;
    private String notes;
    private String resultJson;
    private String citationManifestJson;
    private String evidenceVersion;
    private String dataVersion;
    private Boolean sourceSessionAvailable;
    private String status;
    private Long revision;
    private LocalDateTime trashedAt;
    private LocalDateTime purgeAfter;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime purgedAt;
}
