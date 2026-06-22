package com.opc.platform.caseitem.dto;

import lombok.Data;

@Data
public class CaseItemQueryDTO {

    private String keyword;

    private Long regionId;

    private String category;

    private String status;
}
