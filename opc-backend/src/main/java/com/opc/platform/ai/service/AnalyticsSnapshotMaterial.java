package com.opc.platform.ai.service;

import java.util.List;

/** Server-rebuilt analytics material that may be persisted as a research boundary. */
public record AnalyticsSnapshotMaterial(
        String metricId,
        String dataVersion,
        String filtersJson,
        String payloadJson,
        List<String> allowedBucketIds
) {
    public AnalyticsSnapshotMaterial {
        allowedBucketIds = allowedBucketIds == null ? List.of() : List.copyOf(allowedBucketIds);
    }
}
