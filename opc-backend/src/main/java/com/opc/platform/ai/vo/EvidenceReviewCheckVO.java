package com.opc.platform.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceReviewCheckVO {
    private String key;
    private String label;
    private boolean passed;
    private String message;
}
