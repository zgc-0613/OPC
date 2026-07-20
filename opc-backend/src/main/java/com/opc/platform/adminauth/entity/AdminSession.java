package com.opc.platform.adminauth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_sessions")
public class AdminSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adminId;
    private String token;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
