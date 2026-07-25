package com.opc.platform.policy.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PolicyDetailVO {

    private Long id;

    private String title;

    private Long regionId;

    private String regionName;

    private String issuingBody;

    private String documentNo;

    private LocalDate publishDate;

    private LocalDate effectiveDate;

    private String validPeriod;

    private Long sourceId;

    private String sourceTitle;

    private String policyLevel;

    private String policyType;

    private String applicabilityMode;

    private List<Long> industryTagIds;

    private List<String> industryTagNames;

    private String summary;

    private String keyPoints;

    private String supportMeasures;

    private String tags;

    private String originalUrl;

    private String evidenceUrl;

    private String localFile;

    private LocalDate accessedAt;

    private String status;

    private String reviewer;

    private String aiEvidenceStatus;

    private Long evidenceRevision;

    private LocalDateTime updatedAt;
}
