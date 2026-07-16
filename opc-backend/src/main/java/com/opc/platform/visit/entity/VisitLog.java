package com.opc.platform.visit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("visit_logs")
public class VisitLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String pagePath;

    private String pageTitle;

    private String targetType;

    private Long targetId;

    private String ipAddress;

    private String userAgent;

    private String visitorKey;

    private String referer;

    private LocalDateTime visitedAt;

    private LocalDateTime createdAt;
}