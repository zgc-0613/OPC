package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvidenceReviewBatchItemDTO {

    @NotBlank
    @Pattern(regexp = "case|policy|source")
    private String itemType;

    @NotNull
    @Positive
    private Long itemId;

    @NotBlank
    @Pattern(regexp = "legacy_unverified|verified|excluded")
    private String expectedEvidenceStatus;

    @NotNull
    private LocalDateTime expectedUpdatedAt;

    @NotNull
    @PositiveOrZero
    private Long expectedVersion;
}
