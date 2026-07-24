package com.opc.platform.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EvidenceReviewBatchUpdateDTO {

    @NotEmpty
    @Size(max = 100)
    @Valid
    private List<EvidenceReviewBatchItemDTO> items;

    @NotBlank
    @Pattern(regexp = "legacy_unverified|verified|excluded")
    private String evidenceStatus;

    @Size(max = 500)
    private String notes;

    @Size(max = 500)
    private String reason;

    private boolean cascade;
}
