package com.opc.platform.searchlog.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SearchKeywordVO {

    private String keyword;

    private String searchScope;

    private Long searchCount;

    private Long totalResultCount;

    private LocalDateTime latestSearchedAt;
}
