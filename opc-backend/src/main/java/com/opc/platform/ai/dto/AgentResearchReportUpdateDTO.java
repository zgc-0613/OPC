package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentResearchReportUpdateDTO {
    @NotNull
    @Positive
    private Long expectedRevision;

    @NotBlank
    @Size(max = 120)
    private String title;

    @Size(max = 1000)
    private String notes;
}
