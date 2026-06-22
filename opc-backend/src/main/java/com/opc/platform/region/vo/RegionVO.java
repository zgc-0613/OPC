package com.opc.platform.region.vo;

import lombok.Data;

@Data
public class RegionVO {

    private Long id;

    private String name;

    private String level;

    private Long parentId;

    private Integer sortOrder;
}