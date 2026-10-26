package com.opc.platform.platformsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_sites")
public class PlatformSite {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceKey;
    private String name;
    private String url;
    private String category;
    private String owner;
    private String services;
    private String summary;
    private String domain;
    private String faviconUrl;
    private String placeholderLogoPath;
    private Integer sortOrder;
    private String status;
    private String verificationStatus;
    private String sourceBatch;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
