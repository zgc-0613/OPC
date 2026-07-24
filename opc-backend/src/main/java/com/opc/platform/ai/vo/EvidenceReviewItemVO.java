package com.opc.platform.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    private boolean reviewable;
    private List<String> blockingReasons;
    private Long version;
    private LocalDateTime updatedAt;
}
