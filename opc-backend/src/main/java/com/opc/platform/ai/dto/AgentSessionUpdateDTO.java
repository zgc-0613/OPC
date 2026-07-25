package com.opc.platform.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentSessionUpdateDTO {

    @Size(max = 80)
    private String title;

    private Boolean pinned;
}
