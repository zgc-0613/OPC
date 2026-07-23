package com.opc.platform.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PolicyUpdateDTO {

    @NotBlank
    private String title;

    @NotNull
    private Long regionId;

    @NotBlank
    private String issuingBody;

    private String documentNo;

    private LocalDate publishDate;

    private LocalDate effectiveDate;

    private String validPeriod;

    @NotNull
    private Long sourceId;

    @NotBlank
    private String policyLevel;

    @NotBlank
    private String policyType;

    @NotBlank
    private String summary;

    private String keyPoints;

    private String supportMeasures;

    private String tags;

    private String originalUrl;

    private String evidenceUrl;

    private String localFile;

    @NotNull
    private LocalDate accessedAt;

    @NotBlank
    private String status;

    private String reviewer;

    @Pattern(regexp = "legacy_unverified|verified|excluded")
    private String aiEvidenceStatus;
}
