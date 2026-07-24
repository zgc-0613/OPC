package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IndustryClassificationRequestDTO {

    @NotBlank
    @Size(max = 80)
    private String industry;
}
