package com.opc.platform.policy.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PolicyDetailVO {

    private Long id;

    private String title;

    private Long regionId;

    private String regionName;

    private String issuingBody;

    private LocalDate publishDate;

    private Long sourceId;

    private String sourceTitle;

    private String policyLevel;

    private String policyType;

    private String summary;

    private String keyPoints;

    private String supportMeasures;

    private String tags;

    private String originalUrl;

    private String localFile;

    private LocalDate accessedAt;

    private String status;

    private String reviewer;
}
