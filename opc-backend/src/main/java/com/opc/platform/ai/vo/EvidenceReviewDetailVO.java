package com.opc.platform.ai.vo;

import com.opc.platform.source.entity.Source;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class EvidenceReviewDetailVO {
    private String itemType;
    private Long itemId;
    private String title;
    private String publicationStatus;
    private String evidenceStatus;
    private Long sourceId;
    private String sourceTitle;
    private boolean reviewable;
    private List<String> blockingReasons;
    private List<EvidenceReviewCheckVO> checks;
    private Object content;
    private Source source;
    private List<EvidenceReviewItemVO> relatedItems;
    private List<EvidenceReviewHistoryVO> history;
    private Long version;
    private LocalDateTime updatedAt;
    private String originalUrl;
}
