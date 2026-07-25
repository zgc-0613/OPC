package com.opc.platform.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentSessionCreateDTO {

    @Size(max = 120)
    private String title;

    private JsonNode profile;
}
