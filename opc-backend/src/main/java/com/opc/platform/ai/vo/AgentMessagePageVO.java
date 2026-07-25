package com.opc.platform.ai.vo;

import java.util.List;

public record AgentMessagePageVO(
        List<AgentMessageVO> items,
        Integer nextBeforeSequence,
        boolean hasMore
) {
}
