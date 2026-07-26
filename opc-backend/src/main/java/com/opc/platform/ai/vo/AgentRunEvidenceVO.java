package com.opc.platform.ai.vo;

import java.util.List;
import java.util.Map;

public record AgentRunEvidenceVO(
        Long runId,
        String status,
        List<AgentEvidenceItemVO> items,
        Map<String, Integer> groups
) {
}
