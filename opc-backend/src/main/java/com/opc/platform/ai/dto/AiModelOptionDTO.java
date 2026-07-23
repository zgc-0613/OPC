package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiModelOptionDTO(
        @NotBlank @Size(max = 191) String modelId,
        @NotBlank @Size(max = 191) String displayName
) {
}
