package com.opc.platform.adminauth.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminRegistrationRequestVO {
    private Long id;
    private String username;
    private String status;
    private Long reviewedBy;
    private String reviewedByUsername;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
