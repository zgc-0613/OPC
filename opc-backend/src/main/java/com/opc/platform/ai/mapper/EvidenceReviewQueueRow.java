package com.opc.platform.ai.mapper;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceReviewQueueRow {

    private String itemType;
    private Long itemId;
    private String title;
    private String publicationStatus;
    private String evidenceStatus;
    private Long sourceId;
    private String sourceTitle;
    private String sourceStatus;
    private String sourceEvidenceStatus;
    private String sourcePublisher;
    private String sourceUrl;
    private boolean contentComplete;
    private boolean reviewable;
    private Long version;
    private LocalDateTime updatedAt;
}
