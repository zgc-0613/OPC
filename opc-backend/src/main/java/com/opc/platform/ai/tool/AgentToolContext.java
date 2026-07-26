package com.opc.platform.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AgentToolContext {

    private final Long runId;
    private final Long userId;
    private final String leaseOwner;
    private final Long primaryRegionId;
    private final boolean enforceRegionAuthorization;
    private final Set<Long> allowedSourceIds = new LinkedHashSet<>();
    private final Set<Long> allowedCaseIds = new LinkedHashSet<>();
    private final Set<Long> allowedRegionIds = new LinkedHashSet<>();
    private final Set<Long> evidenceCaseIds = new LinkedHashSet<>();
    private final Set<Long> evidencePolicyIds = new LinkedHashSet<>();
    private final Set<String> exactRegionEvidence = new LinkedHashSet<>();
    private final Set<String> parentRegionEvidence = new LinkedHashSet<>();
    private final Set<String> nationalEvidence = new LinkedHashSet<>();
    private final Set<String> crossRegionEvidence = new LinkedHashSet<>();

    public AgentToolContext(Long runId, Long userId) {
        this(runId, userId, null, null, false);
    }

    public AgentToolContext(Long runId, Long userId, String leaseOwner) {
        this(runId, userId, leaseOwner, null, false);
    }

    public AgentToolContext(Long runId, Long userId, String leaseOwner, Long primaryRegionId) {
        this(runId, userId, leaseOwner, primaryRegionId, true);
    }

    private AgentToolContext(
            Long runId,
            Long userId,
            String leaseOwner,
            Long primaryRegionId,
            boolean enforceRegionAuthorization
    ) {
        this.runId = runId;
        this.userId = userId;
        this.leaseOwner = leaseOwner;
        this.primaryRegionId = primaryRegionId;
        this.enforceRegionAuthorization = enforceRegionAuthorization;
        if (primaryRegionId != null && primaryRegionId > 0) allowedRegionIds.add(primaryRegionId);
    }

    public Long runId() {
        return runId;
    }

    public Long userId() {
        return userId;
    }

    public String leaseOwner() {
        return leaseOwner;
    }

    public Set<Long> allowedSourceIds() {
        return Set.copyOf(allowedSourceIds);
    }

    public Set<Long> allowedCaseIds() {
        return Set.copyOf(allowedCaseIds);
    }

    public Long primaryRegionId() {
        return primaryRegionId;
    }

    void authorizeRegion(Long regionId) {
        if (regionId != null && regionId > 0) allowedRegionIds.add(regionId);
    }

    void requireRegionAuthorized(Long regionId) {
        if (regionId == null || regionId <= 0) {
            throw new AgentToolException("INVALID_REGION_ID", "地区编号无效");
        }
        if (enforceRegionAuthorization && !allowedRegionIds.contains(regionId)) {
            throw new AgentToolException("FORBIDDEN_REGION_ID", "地区编号未在本次研究中授权");
        }
    }

    void accept(String toolName, AgentToolResult result) {
        allowedSourceIds.addAll(result.sourceIds());
        allowedCaseIds.addAll(result.caseIds());
        JsonNode output = result.output();
        if (output == null) return;
        if ("search_cases".equals(toolName) || "compare_cases".equals(toolName)) {
            collectEvidence(output.path("items"), "case", "caseId", evidenceCaseIds);
            collectEvidence(output.path("cases"), "case", "caseId", evidenceCaseIds);
        } else if ("search_policies".equals(toolName)) {
            collectEvidence(output.path("items"), "policy", "policyId", evidencePolicyIds);
        }
    }

    public EvidenceCoverage deriveCoverage(String action) {
        int sourceCount = allowedSourceIds.size();
        String status;
        if ("evidence_insufficient".equals(action) || sourceCount == 0) status = "insufficient";
        else if (!evidenceCaseIds.isEmpty() && !evidencePolicyIds.isEmpty()) status = "sufficient";
        else status = "partial";
        return new EvidenceCoverage(
                status, evidenceCaseIds.size(), evidencePolicyIds.size(), sourceCount,
                exactRegionEvidence.size(), parentRegionEvidence.size(),
                nationalEvidence.size(), crossRegionEvidence.size()
        );
    }

    private void collectEvidence(JsonNode items, String type, String field, Set<Long> target) {
        if (!items.isArray()) return;
        for (JsonNode item : items) {
            JsonNode value = item.path(field);
            if (!value.isIntegralNumber() || value.asLong() <= 0) continue;
            target.add(value.asLong());
            String key = type + ':' + value.asLong();
            switch (item.path("geographicScope").asText("")) {
                case "exact" -> exactRegionEvidence.add(key);
                case "parent" -> parentRegionEvidence.add(key);
                case "national" -> nationalEvidence.add(key);
                case "cross_region" -> crossRegionEvidence.add(key);
                default -> { }
            }
        }
    }

    public record EvidenceCoverage(
            String status,
            int caseCount,
            int policyCount,
            int sourceCount,
            int exactRegionCount,
            int parentRegionCount,
            int nationalCount,
            int crossRegionCount
    ) {
    }
}
