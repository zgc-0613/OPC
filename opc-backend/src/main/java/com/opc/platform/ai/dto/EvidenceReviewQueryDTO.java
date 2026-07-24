package com.opc.platform.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class EvidenceReviewQueryDTO {

    @Pattern(regexp = "case|policy|source", message = "Unsupported evidence item type")
    private String itemType;

    @Pattern(regexp = "legacy_unverified|verified|excluded", message = "Unsupported evidence status")
    private String evidenceStatus;

    @Min(1)
    private int page = 1;

    @Min(1)
    @Max(100)
    private int size = 20;
}
