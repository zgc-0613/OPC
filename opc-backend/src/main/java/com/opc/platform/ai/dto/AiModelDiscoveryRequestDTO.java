package com.opc.platform.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiModelDiscoveryRequestDTO {

    @NotBlank
    @Size(max = 40)
    private String provider;

    @NotBlank
    @Size(max = 40)
    private String apiFormat;

    @NotBlank
    @Size(max = 500)
    private String apiBaseUrl;

    @Size(max = 2048)
    private String apiKey;

    @Min(1)
    @Max(180)
    private Integer timeoutSeconds = 30;
}
