package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EntrepreneurshipAdviceRequestDTO {

    @NotBlank
    @Pattern(regexp = "solo_company|individual_business|small_team|exploring")
    private String ventureType;

    @NotNull
    private Long regionId;

    private Long industryTagId;

    @Size(max = 80)
    private String industry;

    @NotBlank
    @Pattern(regexp = "idea|validation|early_operation|growth")
    private String stage;

    @NotBlank
    @Pattern(regexp = "under_100k|100k_500k|500k_1m|over_1m|undecided")
    private String budgetRange;

    @NotBlank
    @Size(max = 200)
    private String goal;

    @Size(max = 300)
    private String existingResources;

    @Size(max = 500)
    private String userQuestion;
}
