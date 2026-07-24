package com.opc.platform.ai.vo;

import lombok.Data;

import java.util.List;

@Data
public class EvidenceReviewPageVO {

    private List<EvidenceReviewItemVO> items;
    private int page;
    private int size;
    private long total;
}
