package com.opc.platform.ai.tool;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SearchCasesArguments {

    @Pattern(regexp = "selected|cross_region_reference")
    private String scope = "selected";

    @Size(max = 120)
    private String query;

    @Size(max = 50)
    private String category;

    @Min(1)
    @Max(10)
    private Integer limit = 5;
}
