package com.opc.platform.tag.vo;

import lombok.Data;

@Data
public class TagVO {

    private Long id;

    private String name;

    private String tagType;

    private Boolean isIndustry;

    private Integer sortOrder;
}
