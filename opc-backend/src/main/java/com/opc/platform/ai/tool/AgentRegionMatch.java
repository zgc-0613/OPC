package com.opc.platform.ai.tool;

public record AgentRegionMatch(
        Long regionId,
        String regionName,
        String geographicLevel,
        Long parentRegionId,
        String matchReason
) {
}
