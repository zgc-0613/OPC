package com.opc.platform.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CaptchaSettingsUpdateDTO {

    @NotNull
    private Boolean enabled;

    @NotNull
    @Min(1000)
    @Max(50000)
    private Integer cost;

    @NotNull
    @Min(60)
    @Max(900)
    private Integer expiresInSeconds;
}
