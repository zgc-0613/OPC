package com.opc.platform.userauth.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserVO {

    private Long id;

    private String username;

    private String email;

    private String status;

    private Boolean passwordConfigured;

    private Long activeSessionCount;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
}
