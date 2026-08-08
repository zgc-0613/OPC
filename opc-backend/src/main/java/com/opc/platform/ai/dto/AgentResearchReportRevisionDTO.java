package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AgentResearchReportRevisionDTO {
    @NotNull
    @Positive
    private Long expectedRevision;
}
