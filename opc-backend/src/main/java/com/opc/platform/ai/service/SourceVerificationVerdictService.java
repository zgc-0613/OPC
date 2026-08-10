package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.ai.tool.AgentToolException;
import com.opc.platform.ai.tool.PhaseThreeEvidenceBundle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Derives a source-verification verdict from bounded claim relationships, never from a model verdict. */
final class SourceVerificationVerdictService {

    private static final Pattern CLAIM_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");
    private static final Set<String> RELATIONS = Set.of("supports", "contradicts", "unresolved");

    Decision decide(String action, JsonNode legacy, PhaseThreeEvidenceBundle evidence) {
        List<Assessment> assessments = assessments(legacy.path("verificationClaims"), evidence.sourceIds());
        if (evidence.sourceIds().isEmpty() || "evidence_insufficient".equals(action)) {
            return new Decision("insufficient", "当前没有合法授权证据，无法形成有效证据链。",
                    "insufficient", assessments, List.of());
        }
        if (assessments.isEmpty()) {
            return new Decision("insufficient", "当前主张尚未形成可核验的证据链。",
                    "insufficient", List.of(), List.of());
        }

        Map<String, List<Assessment>> byClaim = new LinkedHashMap<>();
        assessments.forEach(item -> byClaim.computeIfAbsent(item.claimId(), ignored -> new ArrayList<>()).add(item));
        List<Conflict> conflicts = new ArrayList<>();
        for (List<Assessment> group : byClaim.values()) {
            Set<Long> supporting = relationSources(group, "supports");
            Set<Long> contradicting = relationSources(group, "contradicts");
            Set<Long> combined = new LinkedHashSet<>(supporting);
            combined.addAll(contradicting);
            if (!supporting.isEmpty() && !contradicting.isEmpty()) {
                Assessment first = group.get(0);
                conflicts.add(new Conflict(first.claimId(), first.text(), List.copyOf(combined)));
            }
        }

        long supports = assessments.stream().filter(item -> "supports".equals(item.relation())).count();
        long contradicts = assessments.stream().filter(item -> "contradicts".equals(item.relation())).count();
        long unresolved = assessments.stream().filter(item -> "unresolved".equals(item.relation())).count();
        if (!conflicts.isEmpty()) {
            return new Decision("conflicting", "已授权来源对同一关键主张给出冲突信息，不能合并为确定结论。",
                    "conflicting", assessments, conflicts);
        }
        if (supports > 0 && (contradicts > 0 || unresolved > 0)) {
            return new Decision("partially_supports", "当前证据只支持部分主张，仍有不支持或待确认的部分。",
                    "partial", assessments, List.of());
        }
        if (supports > 0) {
            return new Decision("supports", "当前关键主张均有本次运行的合法授权引用支持。",
                    "sufficient", assessments, List.of());
        }
        if (contradicts > 0) {
            return new Decision("does_not_support", "当前已授权证据不能支持待核验主张。",
                    "sufficient", assessments, List.of());
        }
        return new Decision("insufficient", "当前主张尚未形成可核验的证据链。",
                "insufficient", assessments, List.of());
    }

    private List<Assessment> assessments(JsonNode values, Set<Long> authorizedSourceIds) {
        if (values.isMissingNode()) return List.of();
        if (!values.isArray() || values.size() > AgentResearchContract.MAX_VERIFICATION_CLAIMS) invalid();
        List<Assessment> result = new ArrayList<>();
        for (JsonNode value : values) {
            String claimId = value.path("claimId").asText("").trim();
            String text = value.path("text").asText("").trim();
            String relation = value.path("relation").asText("");
            if (!value.isObject() || value.size() != 4 || !CLAIM_ID.matcher(claimId).matches()
                    || text.isEmpty() || text.length() > AgentResearchContract.MAX_STATEMENT_LENGTH
                    || !RELATIONS.contains(relation) || !value.path("sourceIds").isArray()
                    || value.path("sourceIds").size() > AgentResearchContract.MAX_SOURCE_IDS_PER_ITEM) invalid();
            Set<Long> sourceIds = new LinkedHashSet<>();
            for (JsonNode sourceId : value.path("sourceIds")) {
                if (!sourceId.isIntegralNumber() || sourceId.asLong() <= 0
                        || !authorizedSourceIds.contains(sourceId.asLong()) || !sourceIds.add(sourceId.asLong())) {
                    invalid();
                }
            }
            if (!"unresolved".equals(relation) && sourceIds.isEmpty()) invalid();
            result.add(new Assessment(claimId, text, relation, List.copyOf(sourceIds)));
        }
        return List.copyOf(result);
    }

    private Set<Long> relationSources(List<Assessment> assessments, String relation) {
        Set<Long> sourceIds = new LinkedHashSet<>();
        assessments.stream().filter(item -> relation.equals(item.relation()))
                .forEach(item -> sourceIds.addAll(item.sourceIds()));
        return sourceIds;
    }

    private void invalid() {
        throw new AgentToolException(AgentResearchContract.INVALID_STRUCTURED_RESULT, "来源核验主张格式无效");
    }

    record Decision(
            String verdict,
            String explanation,
            String evidenceStatus,
            List<Assessment> assessments,
            List<Conflict> conflicts
    ) { }

    record Assessment(String claimId, String text, String relation, List<Long> sourceIds) { }

    record Conflict(String claimId, String text, List<Long> sourceIds) { }
}
