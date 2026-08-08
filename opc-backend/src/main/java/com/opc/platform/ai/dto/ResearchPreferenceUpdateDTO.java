package com.opc.platform.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResearchPreferenceUpdateDTO {
    private Boolean memoryEnabled;

    @Size(max = 120)
    private String commonRegion;

    @Size(max = 120)
    private String commonIndustry;

    @Size(max = 80)
    private String technologyDirection;

    @Size(max = 80)
    private String ventureStage;

    @Size(max = 120)
    private String budgetRange;

    @Size(max = 500)
    private String teamCapabilities;

    @Size(max = 500)
    private String existingResources;

    @Size(max = 500)
    private String policyFocus;
}
