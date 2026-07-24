package com.opc.platform.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EvidenceReviewQueryDTO {

    @Size(max = 100)
    private String keyword;

    @Pattern(regexp = "^(?:case|policy|source)?$", message = "Unsupported evidence item type")
    private String itemType;

    @Pattern(regexp = "^(?:legacy_unverified|verified|excluded)?$", message = "Unsupported evidence status")
    private String evidenceStatus;

    @Pattern(regexp = "all|reviewable|blocked")
    private String reviewability = "all";

    @Positive
    private Long sourceId;

    @Pattern(regexp = "updated_desc|updated_asc|title_asc|title_desc")
    private String sort = "updated_desc";

    @Min(1)
    private int page = 1;

    @Min(1)
    @Max(100)
    private int size = 20;
}
