package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_settings_audit")
public class AiSettingsAudit {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adminId;
    private String adminUsername;
    private String action;
    private String changeSummary;
    private Boolean success;
    private LocalDateTime createdAt;
}
