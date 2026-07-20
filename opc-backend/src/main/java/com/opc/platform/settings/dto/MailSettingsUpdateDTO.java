package com.opc.platform.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class MailSettingsUpdateDTO {

    private Boolean mailEnabled;

    private String siteName;

    private String host;

    @Min(1)
    @Max(65535)
    private Integer port;

    private String username;

    private String password;

    private Boolean clearPassword;

    @Email
    private String fromEmail;

    private String fromName;

    private String securityMode;

    @Min(1)
    @Max(60)
    private Integer timeoutSeconds;

    @Min(1)
    @Max(60)
    private Integer verificationCodeMinutes;

    @Min(10)
    @Max(3600)
    private Integer resendIntervalSeconds;

    @Min(1)
    @Max(365)
    private Integer sessionDays;

    private String verificationSubject;

    private String verificationHtml;
}
