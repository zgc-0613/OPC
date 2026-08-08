package com.opc.platform.ai.service;

import com.opc.platform.ai.vo.AgentSessionVO;

/** Existing research receipt plus the immutable analytics boundary used to start it. */
public record AnalyticsResearchStartReceipt(
        AgentSessionVO session,
        Long messageId,
        Long runId,
        String status,
        Long analyticsSnapshotId,
        String metricId,
        String dataVersion
) {
}
