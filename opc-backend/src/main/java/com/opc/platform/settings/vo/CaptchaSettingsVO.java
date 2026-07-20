package com.opc.platform.settings.vo;

import lombok.Data;

@Data
public class CaptchaSettingsVO {

    private Boolean enabled;

    private String algorithm;

    private Integer cost;

    private Integer expiresInSeconds;

    private Boolean secretConfigured;
}
