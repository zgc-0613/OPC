package com.opc.platform.caseitem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CaseItemUpdateDTO {

    @NotBlank
    private String title;

    @NotNull
    private Long regionId;

    @NotBlank
    private String category;

    private String actorName;

    @NotNull
    private Long sourceId;

    @NotBlank
    private String summary;

    private String businessModel;

    private String aiTools;

    private String outcome;

    private String tags;

    private String originalUrl;

    private String localFile;

    @NotNull
    private LocalDate accessedAt;

    @NotBlank
    private String status;

    private String reviewer;

    @Pattern(regexp = "legacy_unverified|verified|excluded")
    private String aiEvidenceStatus;
}
