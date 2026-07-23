package com.opc.platform.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CaseAnalysisVO {
    private Long analysisId;
    private Long caseId;
    private String summary;
    private String businessModel;
    private String technicalAssessment;
    private List<String> opportunities = new ArrayList<>();
    private List<String> risks = new ArrayList<>();
    private List<String> recommendedActions = new ArrayList<>();
    private List<AiCitationVO> citations = new ArrayList<>();
    private Double confidence;
    private String evidenceStatus;
    private String provider;
    private String model;
    private String promptVersion;
    private LocalDateTime generatedAt;
    private AiTokenUsageVO tokenUsage = new AiTokenUsageVO();
}
