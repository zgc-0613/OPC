package com.opc.platform.policy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("policies")
public class Policy {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private Long regionId;

    private String issuingBody;

    private String documentNo;

    private LocalDate publishDate;

    private LocalDate effectiveDate;

    private String validPeriod;

    private Long sourceId;

    private String policyLevel;

    private String policyType;

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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
