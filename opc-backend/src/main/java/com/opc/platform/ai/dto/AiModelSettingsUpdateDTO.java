package com.opc.platform.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AiModelSettingsUpdateDTO {

    @NotBlank
    @Size(max = 40)
    private String provider;

    @NotBlank
    @Size(max = 40)
    private String apiFormat;

    @Size(max = 500)
    private String apiBaseUrl;

    @Size(max = 191)
    private String modelId;

    @Valid
    @Size(max = 50)
    private List<AiModelOptionDTO> models = List.of();

    @Size(max = 2048)
    private String apiKey;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private Double temperature;

    @NotNull
    @Min(1)
    @Max(65536)
    private Integer maxOutputTokens;

    @NotNull
    @Min(1)
    @Max(180)
    private Integer timeoutSeconds;

    @NotNull
    @Min(0)
    @Max(5)
    private Integer retryCount;

    @NotNull
    @Min(0)
    private Long dailyTokenQuota;

    @NotNull
    private Boolean enabled;

    @NotNull
    private Boolean agentEnabled = false;

    @NotNull
    @Min(1)
    @Max(8)
    private Integer agentMaxModelRounds = 5;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer agentMaxToolCalls = 6;

    @NotNull
    @Min(512)
    @Max(32000)
    private Integer agentMaxTokens = 28000;

    @NotNull
    @Min(1)
    @Max(24)
    private Integer agentHistoryWindow = 12;

    @NotNull
    @Min(10)
    @Max(600)
    private Integer agentTimeoutSeconds = 120;

    @NotBlank
    @Pattern(regexp = "json_plan|native")
    private String agentToolMode = "json_plan";
}
