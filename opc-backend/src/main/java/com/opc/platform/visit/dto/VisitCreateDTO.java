package com.opc.platform.visit.dto;

import lombok.Data;
@Data
public class VisitCreateDTO {
    private String pagePath;
    private String pageTitle;
    private String targetType;
    private Long targetId;
    private String referer;
    
}
