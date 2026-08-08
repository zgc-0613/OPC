package com.opc.platform.ai.service;

/** Internal-only binding from a server-created analytics snapshot to a Run. */
public record AgentAnalyticsSnapshotBinding(
        Long snapshotId,
        String metricId,
        String dataVersion,
        String filtersJson,
        String snapshotJson
) {
}
