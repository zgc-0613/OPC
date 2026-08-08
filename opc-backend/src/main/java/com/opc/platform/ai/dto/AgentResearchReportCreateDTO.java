package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentResearchReportCreateDTO {
    @NotNull
    private Long finalMessageId;
    @NotBlank
    @Size(min = 1, max = 120)
    private String title;
    @Size(max = 1000)
    private String notes;
    @NotBlank
    @Size(min = 8, max = 64)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String idempotencyKey;
}
