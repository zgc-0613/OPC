package com.opc.platform.caseitem.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CaseItemListVO {

    private Long id;

    private String title;

    private Long regionId;

    private String regionName;

    private String category;

    private String actorName;

    private Long sourceId;

    private String sourceTitle;

    private String summary;

    private String tags;

    private LocalDate accessedAt;

    private String status;
}
