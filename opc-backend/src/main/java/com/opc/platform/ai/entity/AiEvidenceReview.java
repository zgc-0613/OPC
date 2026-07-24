package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_evidence_reviews")
public class AiEvidenceReview {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String itemType;
    private Long itemId;
    private String previousStatus;
    private String newStatus;
    private Long adminId;
    private String adminUsername;
    private String actionType;
    private String reason;
    private String notes;
    private String operationId;
    private LocalDateTime createdAt;
}
