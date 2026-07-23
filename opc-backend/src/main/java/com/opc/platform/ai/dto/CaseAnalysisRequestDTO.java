package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CaseAnalysisRequestDTO {

    @NotNull
    private Long caseId;

    @Size(max = 500)
    private String userQuestion;
}
