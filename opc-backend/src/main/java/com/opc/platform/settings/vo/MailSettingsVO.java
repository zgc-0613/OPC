package com.opc.platform.settings.vo;

import lombok.Data;

@Data
public class MailSettingsVO {

    private Boolean mailEnabled;

    private String siteName;

    private String host;

    private Integer port;

    private String username;

    private Boolean passwordConfigured;

    private String fromEmail;

    private String fromName;

    private String securityMode;

    private Integer timeoutSeconds;

    private Integer verificationCodeMinutes;

    private Integer resendIntervalSeconds;

    private Integer sessionDays;

    private String verificationSubject;

    private String verificationHtml;
}
