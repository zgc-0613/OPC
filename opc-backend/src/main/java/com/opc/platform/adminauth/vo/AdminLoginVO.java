package com.opc.platform.adminauth.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminLoginVO {

    private String username;

    private String token;

    private LocalDateTime expiresAt;
}
