package com.opc.platform.ai.tool;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CompareCasesArguments {

    @NotEmpty
    @Size(min = 2, max = 3)
    private List<Long> caseIds;

    @Size(max = 6)
    private List<@Size(max = 40) String> dimensions;
}
