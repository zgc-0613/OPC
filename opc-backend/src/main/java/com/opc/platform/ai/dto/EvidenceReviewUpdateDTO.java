package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceReviewUpdateDTO {

    @NotBlank
    @Pattern(regexp = "legacy_unverified|verified|excluded")
    private String evidenceStatus;

    @NotBlank
    @Pattern(regexp = "legacy_unverified|verified|excluded")
    private String expectedEvidenceStatus;

    @NotNull
    private LocalDateTime expectedUpdatedAt;

    @NotNull
    @PositiveOrZero
    private Long expectedVersion;

    @Size(max = 500)
    private String reason;

    @Size(max = 500)
    private String notes;

    private boolean cascade;
}
