package com.opc.platform.policy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PolicyApplicabilityBatchDTO {

    @NotBlank
    private String applicabilityMode;

    private List<Long> industryTagIds;

    @Valid
    @NotEmpty
    @Size(max = 100)
    private List<PolicyApplicabilityBatchItemDTO> items;
}
