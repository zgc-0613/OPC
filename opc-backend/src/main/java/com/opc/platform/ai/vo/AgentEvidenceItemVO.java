package com.opc.platform.ai.vo;

public record AgentEvidenceItemVO(
        String itemType,
        Long itemId,
        Long sourceId,
        String title,
        String brief,
        String regionName,
        String geographicLevel,
        String industry,
        String matchReason,
        String evidenceStatus,
        String publisher,
        String sourceTitle,
        String originalUrl,
        String detailUrl,
        boolean available
) {
}
