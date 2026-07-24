package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EvidenceReviewUpdateDTO {

    @NotBlank
    @Pattern(regexp = "legacy_unverified|verified|excluded")
    private String evidenceStatus;

    @Size(max = 500)
    private String notes;
}
