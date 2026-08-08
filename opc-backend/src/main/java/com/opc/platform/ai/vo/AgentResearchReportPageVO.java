package com.opc.platform.ai.vo;

import java.util.List;

public record AgentResearchReportPageVO(
        List<AgentResearchReportVO> items,
        String nextCursor,
        boolean hasMore
) {
}
