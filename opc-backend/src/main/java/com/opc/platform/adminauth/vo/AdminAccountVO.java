package com.opc.platform.adminauth.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminAccountVO {
    private Long id;
    private String username;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
