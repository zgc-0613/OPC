package com.opc.platform.ai.mapper;

import lombok.Data;

@Data
public class AgentUsageLedgerRow {
    private Long usedTokens;
    private Long reservedTokens;
}
