package com.opc.platform.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceReviewItemVO {

    private String itemType;
    private Long itemId;
    private String title;
    private String publicationStatus;
    private String evidenceStatus;
    private Long sourceId;
    private boolean sourceEligible;
    private String sourceTitle;
    private String sourceStatus;
    private String sourceEvidenceStatus;
    private LocalDateTime updatedAt;
}
