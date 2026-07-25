package com.opc.platform.ai.vo;

import java.util.List;

public record AgentSessionHistoryPageVO(
        List<AgentSessionVO> items,
        String nextCursor,
        boolean hasMore
) {
}
