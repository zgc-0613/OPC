package com.opc.platform.searchlog.dto;

import lombok.Data;

@Data
public class SearchLogCreateDTO {

    private String keyword;

    private String searchScope;

    private Integer resultCount;

    private String pagePath;
}
