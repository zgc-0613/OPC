package com.opc.platform.caseitem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("case_items")
public class CaseItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private Long regionId;

    private String category;

    private String actorName;

    private Long sourceId;

    private String summary;

    private String businessModel;

    private String aiTools;

    private String outcome;

    private String tags;

    private String originalUrl;

    private String localFile;

    private LocalDate accessedAt;

    private String status;

    private String reviewer;

    private String aiEvidenceStatus;

    private Long evidenceRevision;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
