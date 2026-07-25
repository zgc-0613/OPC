package com.opc.platform.ai.tool;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentCaseSearchRow {
    private Long caseId;
    private String title;
    private String region;
    private String category;
    private String summary;
    private String businessModel;
    private String aiTools;
    private String outcome;
    private Long sourceId;
    private Long caseRevision;
    private Long sourceRevision;
    private LocalDateTime caseUpdatedAt;
    private LocalDateTime sourceUpdatedAt;
}
