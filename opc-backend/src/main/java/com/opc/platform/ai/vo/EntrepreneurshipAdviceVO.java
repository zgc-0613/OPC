package com.opc.platform.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class EntrepreneurshipAdviceVO {

    private Long analysisId;
    private String summary;
    private String recommendedDirection;
    private List<String> opportunities = new ArrayList<>();
    private List<String> risks = new ArrayList<>();
    private List<String> actionPlan = new ArrayList<>();
    private List<AssistantCaseMatchVO> matchedCases = new ArrayList<>();
    private List<AssistantPolicyMatchVO> matchedPolicies = new ArrayList<>();
    private List<AiCitationVO> citations = new ArrayList<>();
    private Double confidence;
    private String evidenceStatus;
    private List<String> evidenceReasons = new ArrayList<>();
    private String provider;
    private String model;
    private String promptVersion;
    private LocalDateTime generatedAt;
    private AiTokenUsageVO tokenUsage = new AiTokenUsageVO();
}
