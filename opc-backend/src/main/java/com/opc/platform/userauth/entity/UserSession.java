package com.opc.platform.userauth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_sessions")
public class UserSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String token;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
}
