package com.opc.platform.platformsite.vo;

import lombok.Data;

@Data
public class PlatformSiteVO {
    private Long id;
    private String name;
    private String url;
    private String category;
    private String owner;
    private String services;
    private String summary;
    private String domain;
    private String logoUrl;
    private Integer sortOrder;
    private String verificationStatus;
}
