package com.opc.platform.ai.vo;

import java.util.List;
import java.util.Map;

public record AgentRunEvidenceVO(
        Long runId,
        String status,
        List<AgentEvidenceItemVO> items,
        Map<String, Integer> groups,
        int availableCount,
        int totalCount,
        int unavailableCount,
        Map<String, Integer> availableGroups,
        Map<String, Integer> totalGroups
) {
}
