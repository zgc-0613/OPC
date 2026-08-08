package com.opc.platform.ai.vo;

import java.time.LocalDateTime;

public record ResearchPreferenceVO(
        Long userId,
        boolean memoryEnabled,
        String commonRegion,
        String commonIndustry,
        String technologyDirection,
        String ventureStage,
        String budgetRange,
        String teamCapabilities,
        String existingResources,
        String policyFocus,
        LocalDateTime updatedAt
) {}
