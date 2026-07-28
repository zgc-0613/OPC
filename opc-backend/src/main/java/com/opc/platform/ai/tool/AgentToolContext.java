package com.opc.platform.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentToolContext {

    private final Long runId;
    private final Long userId;
    private final String leaseOwner;
    private final Long primaryRegionId;
    private final Long primaryIndustryTagId;
    private final String primaryIndustry;
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
    private final Map<String, RequestAuthorization> requestAuthorizations = new LinkedHashMap<>();

    public AgentToolContext(Long runId, Long userId) {
        this(runId, userId, null, null, null, null, false);
    }

    public AgentToolContext(Long runId, Long userId, String leaseOwner) {
        this(runId, userId, leaseOwner, null, null, null, false);
    }

    public AgentToolContext(Long runId, Long userId, String leaseOwner, Long primaryRegionId) {
        this(runId, userId, leaseOwner, primaryRegionId, null, null, true);
    }

    public AgentToolContext(
            Long runId,
            Long userId,
            String leaseOwner,
            Long primaryRegionId,
            Long primaryIndustryTagId,
            String primaryIndustry
    ) {
        this(runId, userId, leaseOwner, primaryRegionId, primaryIndustryTagId, primaryIndustry, true);
    }

    private AgentToolContext(
            Long runId,
            Long userId,
            String leaseOwner,
            Long primaryRegionId,
            Long primaryIndustryTagId,
            String primaryIndustry,
            boolean enforceRegionAuthorization
    ) {
        this.runId = runId;
        this.userId = userId;
        this.leaseOwner = leaseOwner;
        this.primaryRegionId = primaryRegionId;
        this.primaryIndustryTagId = primaryIndustryTagId;
        this.primaryIndustry = primaryIndustry;
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

    public Long primaryIndustryTagId() {
        return primaryIndustryTagId;
    }

    public String primaryIndustry() {
        return primaryIndustry;
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

    public void registerRequestResult(String requestId, String toolName, AgentToolResult result) {
        if (requestId == null || requestId.isBlank() || requestAuthorizations.containsKey(requestId)) {
            throw new AgentToolException("INVALID_DEPENDENCIES", "研究工具请求标识无效");
        }
        Set<Long> policyIds = new LinkedHashSet<>();
        collectIds(result.output() == null ? null : result.output().path("items"), "policyId", policyIds);
        requestAuthorizations.put(requestId, new RequestAuthorization(
                toolName,
                Set.copyOf(result.caseIds()),
                Set.copyOf(policyIds),
                Set.copyOf(result.sourceIds())
        ));
    }

    public boolean dependenciesAuthorize(String toolName, JsonNode arguments, List<String> dependsOn) {
        if (dependsOn == null || dependsOn.isEmpty()) return false;
        Set<Long> dependencyCaseIds = new LinkedHashSet<>();
        Set<Long> dependencySourceIds = new LinkedHashSet<>();
        for (String requestId : dependsOn) {
            RequestAuthorization authorization = requestAuthorizations.get(requestId);
            if (authorization == null || !compatibleDependency(toolName, authorization.toolName())) return false;
            dependencyCaseIds.addAll(authorization.caseIds());
            dependencySourceIds.addAll(authorization.sourceIds());
        }
        if ("compare_cases".equals(toolName)) {
            JsonNode caseIds = arguments == null ? null : arguments.path("caseIds");
            if (caseIds == null || !caseIds.isArray() || caseIds.isEmpty()) return false;
            for (JsonNode caseId : caseIds) {
                if (!caseId.isIntegralNumber() || !dependencyCaseIds.contains(caseId.asLong())) return false;
            }
            return true;
        }
        if ("get_source".equals(toolName)) {
            JsonNode sourceId = arguments == null ? null : arguments.path("sourceId");
            return sourceId != null && sourceId.isIntegralNumber()
                    && dependencySourceIds.contains(sourceId.asLong());
        }
        return false;
    }

    public Map<String, RequestAuthorization> requestAuthorizations() {
        return Map.copyOf(requestAuthorizations);
    }

    public boolean completedTool(String toolName) {
        return requestAuthorizations.values().stream()
                .anyMatch(authorization -> toolName.equals(authorization.toolName()));
    }

    public long completedToolCount(String toolName) {
        return requestAuthorizations.values().stream()
                .filter(authorization -> toolName.equals(authorization.toolName()))
                .count();
    }

    public int searchedCaseCount() {
        return requestAuthorizations.values().stream()
                .filter(authorization -> "search_cases".equals(authorization.toolName()))
                .flatMap(authorization -> authorization.caseIds().stream())
                .collect(java.util.stream.Collectors.toSet()).size();
    }

    public int searchedPolicyCount() {
        return requestAuthorizations.values().stream()
                .filter(authorization -> "search_policies".equals(authorization.toolName()))
                .flatMap(authorization -> authorization.policyIds().stream())
                .collect(java.util.stream.Collectors.toSet()).size();
    }

    public int searchedSourceCount() {
        return requestAuthorizations.values().stream()
                .filter(authorization -> Set.of("search_cases", "search_policies")
                        .contains(authorization.toolName()))
                .flatMap(authorization -> authorization.sourceIds().stream())
                .collect(java.util.stream.Collectors.toSet()).size();
    }

    public EvidenceCoverage deriveCoverage() {
        int sourceCount = allowedSourceIds.size();
        String status;
        if (sourceCount == 0) status = "insufficient";
        else if (!evidenceCaseIds.isEmpty() && !evidencePolicyIds.isEmpty()) status = "sufficient";
        else status = "partial";
        return new EvidenceCoverage(
                status, evidenceCaseIds.size(), evidencePolicyIds.size(), sourceCount,
                exactRegionEvidence.size(), parentRegionEvidence.size(),
                nationalEvidence.size(), crossRegionEvidence.size()
        );
    }

    public EvidenceCoverage deriveCoverage(String ignoredModelAction) {
        return deriveCoverage();
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

    private void collectIds(JsonNode items, String field, Set<Long> target) {
        if (items == null || !items.isArray()) return;
        for (JsonNode item : items) {
            JsonNode value = item.path(field);
            if (value.isIntegralNumber() && value.asLong() > 0) target.add(value.asLong());
        }
    }

    private boolean compatibleDependency(String toolName, String dependencyToolName) {
        if ("compare_cases".equals(toolName)) return "search_cases".equals(dependencyToolName);
        if ("get_source".equals(toolName)) {
            return Set.of("search_cases", "search_policies", "compare_cases")
                    .contains(dependencyToolName);
        }
        return false;
    }

    public record RequestAuthorization(
            String toolName,
            Set<Long> caseIds,
            Set<Long> policyIds,
            Set<Long> sourceIds
    ) {
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
