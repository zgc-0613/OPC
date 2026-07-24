package com.opc.platform.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceReviewHistoryVO {
    private Long id;
    private String previousStatus;
    private String newStatus;
    private String adminUsername;
    private String actionType;
    private String reason;
    private String notes;
    private String operationId;
    private LocalDateTime createdAt;
}
