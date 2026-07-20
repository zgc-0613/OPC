package com.opc.platform.settings.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SmtpTestResultVO {

    private String host;

    private Integer port;

    private String message;
}
