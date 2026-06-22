package com.opc.platform.source.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SourceVO {

    private Long id;

    private String title;

    private String sourceType;

    private String publisher;

    private String url;

    private String localFile;

    private LocalDate accessedAt;

    private String notes;

    private String status;
}
