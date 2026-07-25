package com.opc.platform.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentMessageCreateDTO {

    @NotBlank
    @Size(max = 2000)
    private String content;

    @NotBlank
    @Size(min = 8, max = 64)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String idempotencyKey;
}
