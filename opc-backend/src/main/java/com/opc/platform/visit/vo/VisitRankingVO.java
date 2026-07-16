package com.opc.platform.visit.vo;

import lombok.Data;

@Data
public class VisitRankingVO {
    private Long targetId;

    private String title;

    private Long pv;

    private Long uv;   
}
