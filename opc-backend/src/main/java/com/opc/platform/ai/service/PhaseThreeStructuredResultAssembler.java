package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.ai.tool.PhaseThreeEvidenceBundle;
import com.opc.platform.common.enums.ErrorCode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Converts the provider-compatible v2 synthesis payload into the frozen Phase Three v1 result. */
final class PhaseThreeStructuredResultAssembler {

    private static final List<String> CASE_SECTIONS = List.of(
            "businessModel", "targetCustomers", "revenueModel", "costsAndResources",
            "technicalRoute", "successFactors", "replicableElements",
            "nonReplicableConditions", "userFit", "actions"
    );
    private static final List<String> TECHNOLOGY_SECTIONS = List.of(
            "costStructure", "dataAndInfrastructure", "capabilityGaps", "dependencies",
            "complianceRisks", "operatingRisks", "alternatives", "roadmap", "experiments"
    );
    private static final List<String> POLICY_SECTIONS = List.of(
            "applicableRegions", "applicableIndustries", "validity", "eligibilityConditions",
            "supportMeasures", "conflicts", "expirationRisks", "verificationNeeded"
    );
    private static final List<String> SOURCE_SECTIONS = List.of(
            "publisherAssessment", "supportedClaims", "unsupportedClaims", "conflicts", "invalidityReasons"
    );

    private final ObjectMapper objectMapper;

    PhaseThreeStructuredResultAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode assemble(
            JsonNode legacy,
            String action,
            JsonNode taskContext,
            PhaseThreeEvidenceBundle evidence,
            String evidenceVersion
    ) {
        String taskType = taskContext.path("taskType").asText("");
        if (!AgentResearchContract.REQUESTED_INTENTS.contains(taskType) || "auto".equals(taskType)) invalid();
        String confidence = confidence(legacy.path("confidence").asDouble());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("schemaVersion", "phase3-structured-result-v1");
        result.put("taskType", taskType);
        result.put("directAnswer", legacy.path("directAnswer").asText());
        ArrayNode keyFindings = convertStatements(legacy.path("keyFindings"), "finding", confidence);
        result.set("keyFindings", keyFindings);
        result.set("recommendations", convertRecommendations(legacy.path("recommendations"), confidence));
        result.set("risks", convertTextClaims(legacy.path("risks"), "risk", "inference", confidence));
        result.set("assumptions", convertTextClaims(legacy.path("assumptions"), "assumption", "methodology", confidence));
        result.set("uncertainties", convertTextClaims(legacy.path("uncertainties"), "uncertainty", "methodology", confidence));
        result.set("nextQuestions", legacy.path("nextQuestions").deepCopy());

        ArrayNode citations = citations(legacy.path("citations"), evidence);
        result.set("citations", citations);
        result.set("taskSelectedEvidence", selectedEvidence(taskType, taskContext));
        result.set("authorizedEvidence", authorizedEvidence(evidence));
        result.put("confidence", confidence);
        result.put("evidenceVersion", evidenceVersion);
        result.putNull("dataVersion");
        result.put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        result.set("taskResult", taskResult(taskType, action, taskContext, legacy, evidence, confidence));

        ensureCitationUse(result, legacy.path("citations"), keyFindings, confidence);
        validateSelectedEvidence(result);
        validateClaimsAndProvenance(result, evidence);
        result.set("evidenceCoverage", evidenceCoverage(result, citations));
        return result;
    }

    private ObjectNode taskResult(
            String taskType,
            String action,
            JsonNode taskContext,
            JsonNode legacy,
            PhaseThreeEvidenceBundle evidence,
            String confidence
    ) {
        return switch (taskType) {
            case "case_analysis" -> caseAnalysis(taskContext, legacy, evidence, confidence);
            case "case_comparison" -> caseComparison(taskContext, legacy, evidence, confidence);
            case "technology_assessment" -> technologyAssessment(taskContext, legacy, evidence, confidence);
            case "policy_lookup" -> policyLookup(legacy, evidence, confidence);
            case "source_verification" -> sourceVerification(
                    action, taskContext, legacy, evidence, confidence);
            case "general_research" -> generalResearch(legacy, confidence);
            default -> throw invalidException();
        };
    }

    private ObjectNode caseAnalysis(
            JsonNode taskContext,
            JsonNode legacy,
            PhaseThreeEvidenceBundle evidence,
            String confidence
    ) {
        long caseId = firstId(taskContext.path("caseIds"));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("type", "case_analysis");
        result.put("caseId", caseId);
        result.put("evidenceStatus", evidence.caseIds().contains(caseId) ? "partial" : "insufficient");
        ObjectNode sections = result.putObject("sections");
        ArrayNode insights = convertStatements(legacy.path("caseInsights"), "case_insight", confidence);
        for (String field : CASE_SECTIONS) {
            sections.set(field, "businessModel".equals(field)
                    ? evidenceSection(insights, "该维度尚缺少可归属证据。")
                    : unknownSection("该维度尚缺少可归属证据。"));
        }
        return result;
    }

    private ObjectNode caseComparison(
            JsonNode taskContext,
            JsonNode legacy,
            PhaseThreeEvidenceBundle evidence,
            String confidence
    ) {
        List<Long> caseIds = ids(taskContext.path("caseIds"));
        List<String> dimensions = strings(taskContext.path("comparisonDimensions"));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("type", "case_comparison");
        setIds(result.putArray("caseIds"), caseIds);
        ArrayNode dimensionArray = result.putArray("dimensions");
        dimensions.forEach(dimensionArray::add);
        ArrayNode baselines = result.putArray("baselines");
        caseIds.forEach(caseId -> {
            ObjectNode baseline = baselines.addObject();
            baseline.put("caseId", caseId);
            baseline.put("evidenceStatus", evidence.caseIds().contains(caseId) ? "partial" : "insufficient");
            baseline.putArray("missingFields");
        });
        result.set("commonalities", unknownSection("尚无统一口径的共同点证据。"));
        result.set("differences", unknownSection("差异结论仅在所选比较维度内成立。"));
        ArrayNode modelComparisons = convertStatements(legacy.path("comparison"), "comparison", confidence);
        ArrayNode comparisons = result.putArray("comparisons");
        for (int index = 0; index < dimensions.size(); index++) {
            ObjectNode comparison = comparisons.addObject();
            comparison.put("dimension", dimensions.get(index));
            comparison.set("analysis", index == 0
                    ? evidenceSection(modelComparisons, "该维度尚缺少比较证据。")
                    : unknownSection("该维度尚缺少比较证据。"));
        }
        result.put("revenueComparability", "unknown");
        result.set("regionalAndPolicyContext", unknownSection("缺少一致的地区与政策上下文。"));
        result.set("userFit", unknownSection("缺少用户条件适配证据。"));
        result.set("conclusion", unknownSection("结论仅限当前已核验证据范围。"));
        return result;
    }

    private ObjectNode technologyAssessment(
            JsonNode taskContext,
            JsonNode legacy,
            PhaseThreeEvidenceBundle evidence,
            String confidence
    ) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("type", "technology_assessment");
        ObjectNode technology = result.putObject("technology");
        if (taskContext.path("technologyTagId").isIntegralNumber()) {
            technology.put("tagId", taskContext.path("technologyTagId").asLong());
        } else {
            technology.putNull("tagId");
        }
        if (taskContext.path("technologyText").isTextual()) {
            technology.put("text", taskContext.path("technologyText").asText());
        } else {
            technology.putNull("text");
        }
        ArrayNode dimensions = result.putArray("dimensions");
        for (String dimension : List.of("maturity", "scenario_fit", "implementation_complexity")) {
            ObjectNode item = dimensions.addObject();
            item.put("dimension", dimension);
            item.put("level", "unknown");
            item.set("rationale", methodologyClaim(
                    "assessment_" + dimension, "该评分维度仍需补充可归属证据。", confidence));
            item.put("confidence", "low");
            item.put("missingEvidence", true);
        }
        ArrayNode caseInsights = convertStatements(legacy.path("caseInsights"), "technology_case", confidence);
        ArrayNode policyInsights = convertStatements(legacy.path("policyInsights"), "technology_policy", confidence);
        for (String field : TECHNOLOGY_SECTIONS) {
            if ("dataAndInfrastructure".equals(field)) {
                result.set(field, evidenceSection(caseInsights, "缺少数据与基础设施证据。"));
            } else if ("complianceRisks".equals(field)) {
                result.set(field, evidenceSection(policyInsights, "缺少合规证据。"));
            } else {
                result.set(field, unknownSection("该技术维度尚缺少证据。"));
            }
        }
        setIds(result.putArray("supportingCases"), evidence.caseIds().stream().sorted().limit(6).toList());
        setIds(result.putArray("relatedPolicies"), evidence.policyIds().stream().sorted().limit(6).toList());
        return result;
    }

    private ObjectNode policyLookup(JsonNode legacy, PhaseThreeEvidenceBundle evidence, String confidence) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("type", "policy_lookup");
        setIds(result.putArray("policyIds"), evidence.policyIds().stream().sorted().limit(6).toList());
        ArrayNode insights = convertStatements(legacy.path("policyInsights"), "policy_insight", confidence);
        for (String field : POLICY_SECTIONS) {
            result.set(field, "supportMeasures".equals(field)
                    ? evidenceSection(insights, "缺少可归属的政策支持证据。")
                    : unknownSection("该政策维度尚缺少证据。"));
        }
        return result;
    }

    private ObjectNode sourceVerification(
            String action,
            JsonNode taskContext,
            JsonNode legacy,
            PhaseThreeEvidenceBundle evidence,
            String confidence
    ) {
        boolean selected = taskContext.path("sourceId").isIntegralNumber();
        ObjectNode result = objectMapper.createObjectNode();
        result.put("type", "source_verification");
        result.put("mode", selected ? "selected_source" : "claim_search");
        if (selected) result.put("sourceId", taskContext.path("sourceId").asLong());
        else result.putNull("sourceId");
        result.put("verdict", "evidence_insufficient".equals(action) ? "insufficient" : "supports");
        for (String field : SOURCE_SECTIONS) {
            result.set(field, unknownSection("该核验维度没有额外的结构化证据。"));
        }
        return result;
    }

    private ObjectNode generalResearch(JsonNode legacy, String confidence) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("type", "general_research");
        ArrayNode sections = result.putArray("sections");
        addResearchSection(sections, "case_evidence", "案例证据",
                convertStatements(legacy.path("caseInsights"), "case_insight", confidence));
        addResearchSection(sections, "policy_evidence", "政策证据",
                convertStatements(legacy.path("policyInsights"), "policy_insight", confidence));
        addResearchSection(sections, "comparison", "比较结论",
                convertStatements(legacy.path("comparison"), "comparison", confidence));
        if (sections.isEmpty()) {
            ObjectNode section = sections.addObject();
            section.put("id", "research_scope");
            section.put("title", "研究范围");
            section.set("content", unknownSection("当前结果没有额外的分项证据。"));
        }
        return result;
    }

    private void addResearchSection(ArrayNode target, String id, String title, ArrayNode items) {
        if (items.isEmpty()) return;
        ObjectNode section = target.addObject();
        section.put("id", id);
        section.put("title", title);
        section.set("content", evidenceSection(items, "该研究分项尚缺少证据。"));
    }

    private ArrayNode convertStatements(JsonNode values, String prefix, String confidence) {
        ArrayNode result = objectMapper.createArrayNode();
        if (!values.isArray()) return result;
        int index = 0;
        for (JsonNode value : values) {
            ObjectNode claim = result.addObject();
            claim.put("id", prefix + '_' + (++index));
            claim.put("kind", value.path("evidenceType").asText());
            claim.put("text", value.path("text").asText());
            claim.set("sourceIds", value.path("sourceIds").deepCopy());
            claim.put("confidence", confidence);
            claim.put("missingEvidence", value.path("sourceIds").isEmpty());
        }
        return result;
    }

    private ArrayNode convertRecommendations(JsonNode values, String confidence) {
        ArrayNode result = objectMapper.createArrayNode();
        if (!values.isArray()) return result;
        int index = 0;
        for (JsonNode value : values) {
            String text = value.path("reason").asText() + "；下一步：" + value.path("nextAction").asText();
            if (text.length() > 320) invalid();
            ObjectNode claim = result.addObject();
            claim.put("id", "recommendation_" + (++index));
            claim.put("kind", "inference");
            claim.put("text", text);
            claim.set("sourceIds", value.path("sourceIds").deepCopy());
            claim.put("confidence", confidence);
            claim.put("missingEvidence", value.path("sourceIds").isEmpty());
        }
        return result;
    }

    private ArrayNode convertTextClaims(JsonNode values, String prefix, String kind, String confidence) {
        ArrayNode result = objectMapper.createArrayNode();
        if (!values.isArray()) return result;
        int index = 0;
        for (JsonNode value : values) {
            ObjectNode claim = result.addObject();
            claim.put("id", prefix + '_' + (++index));
            claim.put("kind", kind);
            claim.put("text", value.asText());
            claim.putArray("sourceIds");
            claim.put("confidence", confidence);
            claim.put("missingEvidence", true);
        }
        return result;
    }

    private ObjectNode methodologyClaim(String id, String text, String confidence) {
        ObjectNode claim = objectMapper.createObjectNode();
        claim.put("id", id);
        claim.put("kind", "methodology");
        claim.put("text", text);
        claim.putArray("sourceIds");
        claim.put("confidence", confidence);
        claim.put("missingEvidence", true);
        return claim;
    }

    private ObjectNode evidenceSection(ArrayNode items, String caveat) {
        if (items == null || items.isEmpty()) return unknownSection(caveat);
        ObjectNode section = objectMapper.createObjectNode();
        section.put("status", "known");
        section.set("items", items);
        section.putNull("caveat");
        return section;
    }

    private ObjectNode unknownSection(String caveat) {
        ObjectNode section = objectMapper.createObjectNode();
        section.put("status", "unknown");
        section.putArray("items");
        section.put("caveat", caveat);
        return section;
    }

    private ArrayNode citations(JsonNode values, PhaseThreeEvidenceBundle evidence) {
        ArrayNode result = objectMapper.createArrayNode();
        if (!values.isArray()) return result;
        Set<Long> unique = new LinkedHashSet<>();
        for (JsonNode value : values) {
            long sourceId = value.path("sourceId").asLong();
            if (!unique.add(sourceId)) invalid();
            PhaseThreeEvidenceBundle.SourceEvidence source = evidence.source(sourceId);
            if (source == null) invalid();
            ObjectNode citation = result.addObject();
            citation.put("sourceId", sourceId);
            citation.put("title", source.title());
            if (source.publisher() == null) citation.putNull("publisher");
            else citation.put("publisher", source.publisher());
            citation.put("url", source.url());
            citation.put("evidenceRevision", source.evidenceRevision());
            citation.put("availability", "current");
        }
        return result;
    }

    private ObjectNode selectedEvidence(String taskType, JsonNode taskContext) {
        ObjectNode selected = objectMapper.createObjectNode();
        ArrayNode caseIds = selected.putArray("caseIds");
        if (Set.of("case_analysis", "case_comparison").contains(taskType)) {
            taskContext.path("caseIds").forEach(caseIds::add);
        }
        selected.putArray("policyIds");
        ArrayNode sourceIds = selected.putArray("sourceIds");
        if ("source_verification".equals(taskType) && taskContext.path("sourceId").isIntegralNumber()) {
            sourceIds.add(taskContext.path("sourceId").asLong());
        }
        return selected;
    }

    private ObjectNode authorizedEvidence(PhaseThreeEvidenceBundle evidence) {
        ObjectNode authorized = objectMapper.createObjectNode();
        setIds(authorized.putArray("caseIds"), evidence.caseIds().stream().sorted().toList());
        setIds(authorized.putArray("policyIds"), evidence.policyIds().stream().sorted().toList());
        setIds(authorized.putArray("sourceIds"), evidence.sourceIds().stream().sorted().toList());
        return authorized;
    }

    private void ensureCitationUse(
            ObjectNode result,
            JsonNode legacyCitations,
            ArrayNode keyFindings,
            String confidence
    ) {
        Set<Long> used = claimSourceIds(result);
        if (!legacyCitations.isArray()) return;
        int index = keyFindings.size();
        for (JsonNode citation : legacyCitations) {
            long sourceId = citation.path("sourceId").asLong();
            if (used.contains(sourceId)) continue;
            if (keyFindings.size() >= 2) invalid();
            ObjectNode claim = keyFindings.addObject();
            claim.put("id", "citation_claim_" + (++index));
            claim.put("kind", "fact");
            claim.put("text", citation.path("claim").asText());
            claim.putArray("sourceIds").add(sourceId);
            claim.put("confidence", confidence);
            claim.put("missingEvidence", false);
            used.add(sourceId);
        }
    }

    private void validateSelectedEvidence(ObjectNode result) {
        Set<Long> authorizedCases = new LinkedHashSet<>(ids(result.path("authorizedEvidence").path("caseIds")));
        Set<Long> authorizedSources = new LinkedHashSet<>(ids(result.path("authorizedEvidence").path("sourceIds")));
        if (!authorizedCases.containsAll(ids(result.path("taskSelectedEvidence").path("caseIds")))
                || !authorizedSources.containsAll(ids(result.path("taskSelectedEvidence").path("sourceIds")))) {
            invalid();
        }
    }

    private void validateClaimsAndProvenance(ObjectNode result, PhaseThreeEvidenceBundle evidence) {
        List<JsonNode> claims = claims(result);
        if (claims.size() > AgentResearchContract.MAX_STATEMENTS || evidenceSections(result) > 10) invalid();
        Set<Long> authorized = evidence.sourceIds();
        Set<Long> citationIds = new LinkedHashSet<>(ids(result.path("citations"), "sourceId"));
        Set<Long> allClaimSources = new LinkedHashSet<>();
        for (JsonNode claim : claims) {
            List<Long> sourceIds = ids(claim.path("sourceIds"));
            if (!authorized.containsAll(sourceIds)) invalid();
            allClaimSources.addAll(sourceIds);
            if ("fact".equals(claim.path("kind").asText())) {
                if (sourceIds.isEmpty() || claim.path("missingEvidence").asBoolean(true)
                        || !citationIds.containsAll(sourceIds)) invalid();
            }
        }
        if (!allClaimSources.containsAll(citationIds)) invalid();

        JsonNode taskResult = result.path("taskResult");
        if ("case_analysis".equals(taskResult.path("type").asText())) {
            long caseId = taskResult.path("caseId").asLong();
            for (JsonNode claim : claims(taskResult.path("sections"))) {
                if (!"fact".equals(claim.path("kind").asText())) continue;
                for (long sourceId : ids(claim.path("sourceIds"))) {
                    if (!evidence.sourceSupportsCase(sourceId, caseId)) invalid();
                }
            }
        } else if ("case_comparison".equals(taskResult.path("type").asText())) {
            List<Long> caseIds = ids(taskResult.path("caseIds"));
            for (JsonNode comparison : taskResult.path("comparisons")) {
                for (JsonNode claim : claims(comparison.path("analysis"))) {
                    if (!"fact".equals(claim.path("kind").asText())) continue;
                    List<Long> sourceIds = ids(claim.path("sourceIds"));
                    for (long caseId : caseIds) {
                        if (sourceIds.stream().noneMatch(sourceId -> evidence.sourceSupportsCase(sourceId, caseId))) {
                            invalid();
                        }
                    }
                }
            }
        } else if ("policy_lookup".equals(taskResult.path("type").asText())) {
            List<Long> policyIds = ids(taskResult.path("policyIds"));
            for (JsonNode claim : claims(taskResult)) {
                if (!"fact".equals(claim.path("kind").asText())) continue;
                for (long sourceId : ids(claim.path("sourceIds"))) {
                    if (policyIds.stream().noneMatch(policyId -> evidence.sourceSupportsPolicy(sourceId, policyId))) {
                        invalid();
                    }
                }
            }
        }
    }

    private ObjectNode evidenceCoverage(ObjectNode result, ArrayNode citations) {
        List<JsonNode> claims = claims(result);
        Set<Long> cited = new LinkedHashSet<>(ids(citations, "sourceId"));
        int factCount = 0;
        int citedFactCount = 0;
        int missing = 0;
        for (JsonNode claim : claims) {
            if (!"fact".equals(claim.path("kind").asText())) continue;
            factCount++;
            List<Long> sources = ids(claim.path("sourceIds"));
            if (!sources.isEmpty() && cited.containsAll(sources)) citedFactCount++;
            else missing++;
        }
        ObjectNode coverage = objectMapper.createObjectNode();
        coverage.put("factClaimCount", factCount);
        coverage.put("citedFactClaimCount", citedFactCount);
        coverage.put("missingEvidenceFactCount", missing);
        if (factCount == 0) coverage.putNull("ratio");
        else coverage.put("ratio", (double) citedFactCount / factCount);
        return coverage;
    }

    private List<JsonNode> claims(JsonNode root) {
        List<JsonNode> claims = new ArrayList<>();
        collectClaims(root, claims);
        return claims;
    }

    private void collectClaims(JsonNode value, List<JsonNode> target) {
        if (value == null) return;
        if (value.isObject() && value.path("id").isTextual() && value.path("kind").isTextual()
                && value.path("sourceIds").isArray()) {
            target.add(value);
            return;
        }
        value.elements().forEachRemaining(child -> collectClaims(child, target));
    }

    private int evidenceSections(JsonNode root) {
        if (root == null) return 0;
        int count = root.isObject() && root.path("status").isTextual()
                && root.path("items").isArray() && root.has("caveat") ? 1 : 0;
        var children = root.elements();
        while (children.hasNext()) count += evidenceSections(children.next());
        return count;
    }

    private Set<Long> claimSourceIds(JsonNode result) {
        Set<Long> ids = new LinkedHashSet<>();
        claims(result).forEach(claim -> ids.addAll(ids(claim.path("sourceIds"))));
        return ids;
    }

    private String confidence(double value) {
        if (value >= 0.75D) return "high";
        if (value >= 0.4D) return "medium";
        return "low";
    }

    private long firstId(JsonNode values) {
        List<Long> ids = ids(values);
        if (ids.isEmpty()) invalid();
        return ids.get(0);
    }

    private List<Long> ids(JsonNode values) {
        List<Long> ids = new ArrayList<>();
        if (values != null && values.isArray()) values.forEach(value -> ids.add(value.asLong()));
        return List.copyOf(ids);
    }

    private List<Long> ids(JsonNode values, String field) {
        List<Long> ids = new ArrayList<>();
        if (values != null && values.isArray()) values.forEach(value -> ids.add(value.path(field).asLong()));
        return List.copyOf(ids);
    }

    private List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        if (values != null && values.isArray()) values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private void setIds(ArrayNode target, List<Long> ids) {
        ids.forEach(target::add);
    }

    private void invalid() {
        throw invalidException();
    }

    private AgentOrchestratorException invalidException() {
        return new AgentOrchestratorException(
                ErrorCode.UPSTREAM_ERROR, AgentResearchContract.INVALID_STRUCTURED_RESULT,
                "结构化研究结果不符合 Phase Three 契约");
    }
}
