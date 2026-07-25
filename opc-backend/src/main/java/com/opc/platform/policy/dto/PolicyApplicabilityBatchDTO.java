package com.opc.platform.policy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PolicyApplicabilityBatchDTO {

    @NotBlank
    private String applicabilityMode;

    private List<Long> industryTagIds;

    @Valid
    @NotEmpty
    private List<PolicyApplicabilityBatchItemDTO> items;
}
