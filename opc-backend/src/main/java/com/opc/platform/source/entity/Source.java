package com.opc.platform.source.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sources")
public class Source {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String sourceType;

    private String publisher;

    private String url;

    private String localFile;

    private LocalDate accessedAt;

    private String notes;

    private String status;

    private String aiEvidenceStatus;

    private Long evidenceRevision;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
