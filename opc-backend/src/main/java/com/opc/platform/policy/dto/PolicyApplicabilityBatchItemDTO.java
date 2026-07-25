package com.opc.platform.policy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PolicyApplicabilityBatchItemDTO {

    @NotNull
    private Long policyId;

    @NotNull
    private Long expectedEvidenceRevision;

    @NotNull
    private LocalDateTime expectedUpdatedAt;
}
