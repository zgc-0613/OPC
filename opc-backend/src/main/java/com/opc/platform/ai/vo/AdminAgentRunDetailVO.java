package com.opc.platform.ai.vo;

import java.util.List;

public record AdminAgentRunDetailVO(
        AdminAgentRunRowVO run,
        List<AgentToolCallSummaryVO> tools
) {
}
