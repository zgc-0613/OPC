package com.opc.platform.ai.vo;

import com.opc.platform.tag.vo.IndustryResolution;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EntrepreneurshipReadinessVO {

    private boolean modelAvailable;
    private boolean evidenceAvailable;
    private String readinessStatus;
    private IndustryResolution resolvedIndustryTag;
    private String matchMethod;
    private double confidence;
    private int verifiedCaseCount;
    private int verifiedPolicyCount;
    private int verifiedSourceCount;
    private int totalRelevantCount;
    private int selectedEvidenceCount;
    private int directRegionCount;
    private int broaderRegionCount;
    private int exactRegionCount;
    private int parentRegionCount;
    private int nationalCount;
    private int crossRegionCount;
    private List<String> reasons = new ArrayList<>();
}
