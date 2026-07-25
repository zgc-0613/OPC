package com.opc.platform.source.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SourceUpdateDTO {

    @NotBlank
    private String title;

    @NotBlank
    private String sourceType;

    private String publisher;

    private String url;

    private String localFile;

    @NotNull
    private LocalDate accessedAt;

    private String notes;

    @NotBlank
    @Pattern(regexp = "draft|pending|published|archived")
    private String status;

    @NotNull
    private Long expectedEvidenceRevision;

    @NotNull
    private LocalDateTime expectedUpdatedAt;

}
