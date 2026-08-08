package com.opc.platform.ai.mapper;

import lombok.Data;

@Data
public class AdminAgentQualityFeedbackRow {
    private String rating;
    private String reason;
    private Long feedbackCount;
}
