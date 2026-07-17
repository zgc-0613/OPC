package com.opc.platform.userauth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_verification_codes")
public class EmailVerificationCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    private String code;

    private String purpose;

    private Boolean used;

    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    private LocalDateTime createdAt;
}
