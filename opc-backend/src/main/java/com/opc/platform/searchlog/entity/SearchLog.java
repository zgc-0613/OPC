package com.opc.platform.searchlog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("search_logs")
public class SearchLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyword;

    private String searchScope;

    private Integer resultCount;

    private String pagePath;

    private String ipAddress;

    private String userAgent;

    private String visitorKey;

    private LocalDateTime searchedAt;

    private LocalDateTime createdAt;
}
