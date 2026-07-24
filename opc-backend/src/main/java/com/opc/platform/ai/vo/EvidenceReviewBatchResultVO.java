package com.opc.platform.ai.vo;

import lombok.Data;

import java.util.List;

@Data
public class EvidenceReviewBatchResultVO {

    private int processedCount;
    private List<EvidenceReviewItemVO> items;
}
