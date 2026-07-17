package com.opc.platform.userauth.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserLoginVO {

    private Long userId;

    private String username;

    private String email;

    private String token;

    private LocalDateTime expiresAt;
}
