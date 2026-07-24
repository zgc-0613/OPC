package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EntrepreneurshipReadinessRequestDTO {

    @NotNull
    private Long regionId;

    private Long industryTagId;

    @Size(max = 80)
    private String industry;
}
