package com.opc.platform.ai.dto;

import lombok.Data;

@Data
public class AgentRunFeedbackUpdateDTO {
    private String rating;
    private String reason;
    private String comment;
    private Long expectedRevision;
}
