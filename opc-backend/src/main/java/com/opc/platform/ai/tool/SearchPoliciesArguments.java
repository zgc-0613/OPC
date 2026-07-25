package com.opc.platform.ai.tool;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SearchPoliciesArguments {

    @NotNull
    private Long regionId;

    private Long industryTagId;

    @Size(max = 100)
    private String industry;

    @Size(max = 120)
    private String keywords;

    @Min(1)
    @Max(10)
    private Integer limit = 5;
}
