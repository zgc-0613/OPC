package com.opc.platform.ai.vo;

import java.util.List;

public record AgentSessionDetailVO(
        AgentSessionVO session,
        List<AgentMessageVO> messages,
        Integer nextBeforeSequence,
        boolean hasMoreMessages,
        AgentRunStatusVO activeRun,
        AgentRunStatusVO latestRun
) {
}
