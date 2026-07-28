package com.opc.platform.ai.tool;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SearchPoliciesArguments {

    @Pattern(regexp = "selected|parent|national")
    private String scope = "selected";

    @Size(max = 120)
    private String query;

    @Min(1)
    @Max(10)
    private Integer limit = 5;
}
