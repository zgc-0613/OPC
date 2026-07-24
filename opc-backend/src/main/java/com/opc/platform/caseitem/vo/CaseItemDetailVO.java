package com.opc.platform.caseitem.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CaseItemDetailVO {

    private Long id;

    private String title;

    private Long regionId;

    private String regionName;

    private String category;

    private String actorName;

    private Long sourceId;

    private String sourceTitle;

    private String summary;

    private String businessModel;

    private String aiTools;

    private String outcome;

    private String tags;

    private String originalUrl;

    private String localFile;

    private LocalDate accessedAt;

    private String status;

    private String reviewer;

    private String aiEvidenceStatus;

    private Long evidenceRevision;

    private LocalDateTime updatedAt;
}
