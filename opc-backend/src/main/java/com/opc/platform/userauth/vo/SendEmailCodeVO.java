package com.opc.platform.userauth.vo;

import lombok.Data;

@Data
public class SendEmailCodeVO {

    private String email;

    private Integer expiresInMinutes;

    private String devCode;
}
