package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Validates the user-selected research boundary before a session exists. The
 * normalized JSON is the only representation persisted on a Phase Three session.
 */
public class PhaseThreeTaskContextValidator {

    public static final String VERSION = "phase3-task-v1";
    private static final int MAX_BYTES = 8_000;
    private static final Set<String> TASK_TYPES = Set.of(
            "case_analysis", "case_comparison", "technology_assessment",
            "policy_lookup", "source_verification", "general_research"
    );
    private static final Set<String> OUTPUT_DEPTHS = Set.of("concise", "standard", "deep");
    private static final Set<String> FIELDS = Set.of(
            "version", "taskType", "caseIds", "comparisonDimensions", "outputDepth",
            "technologyTagId", "technologyText", "sourceId", "applicationScenario",
            "teamCapabilities", "timeline", "existingResources", "constraints"
    );

    private final ObjectMapper objectMapper;

    public PhaseThreeTaskContextValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PhaseThreeTaskContext validateAndNormalize(JsonNode raw, String requestedIntent) {
        if (raw == null || raw.isNull() || !raw.isObject()) {
            throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        }
        rejectUnknownFields(raw);
        String version = requiredText(raw, "version", 40);
        if (!VERSION.equals(version)) throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        String taskType = requiredText(raw, "taskType", 40);
        if (!TASK_TYPES.contains(taskType)) throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        if (!taskType.equals(requestedIntent)) throw invalid("PHASE3_TASK_INTENT_MISMATCH");

        List<Long> caseIds = uniqueIds(raw.path("caseIds"), "caseIds", 3);
        List<String> dimensions = uniqueDimensions(raw.path("comparisonDimensions"));
        String outputDepth = optionalText(raw, "outputDepth", 20);
        if (!StringUtils.hasText(outputDepth)) outputDepth = "standard";
        if (!OUTPUT_DEPTHS.contains(outputDepth)) throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        Long technologyTagId = optionalPositiveLong(raw, "technologyTagId");
        String technologyText = optionalText(raw, "technologyText", 120);
        Long sourceId = optionalPositiveLong(raw, "sourceId");

        validateCrossFieldRules(taskType, caseIds, dimensions, technologyTagId, technologyText, sourceId);

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("version", VERSION);
        normalized.put("taskType", taskType);
        ArrayNode normalizedCases = normalized.putArray("caseIds");
        caseIds.forEach(normalizedCases::add);
        ArrayNode normalizedDimensions = normalized.putArray("comparisonDimensions");
        dimensions.forEach(normalizedDimensions::add);
        normalized.put("outputDepth", outputDepth);
        putOptional(normalized, "technologyTagId", technologyTagId);
        putOptional(normalized, "technologyText", technologyText);
        putOptional(normalized, "sourceId", sourceId);
        putOptional(normalized, "applicationScenario", optionalText(raw, "applicationScenario", 500));
        putOptional(normalized, "teamCapabilities", optionalText(raw, "teamCapabilities", 500));
        putOptional(normalized, "timeline", optionalText(raw, "timeline", 120));
        putOptional(normalized, "existingResources", optionalText(raw, "existingResources", 500));
        putOptional(normalized, "constraints", optionalText(raw, "constraints", 800));

        String canonical = canonicalJson(normalized);
        if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(canonical);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read normalized task context", exception);
        }
        return new PhaseThreeTaskContext(taskType, node, canonical, sha256(canonical));
    }

    private void rejectUnknownFields(JsonNode raw) {
        Iterator<String> fields = raw.fieldNames();
        while (fields.hasNext()) {
            if (!FIELDS.contains(fields.next())) throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        }
    }

    private void validateCrossFieldRules(
            String taskType,
            List<Long> caseIds,
            List<String> dimensions,
            Long technologyTagId,
            String technologyText,
            Long sourceId
    ) {
        if ("case_analysis".equals(taskType)
                && (caseIds.size() != 1 || !dimensions.isEmpty())) {
            throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        }
        if ("case_comparison".equals(taskType)
                && (caseIds.size() < 2 || caseIds.size() > 3 || dimensions.isEmpty())) {
            throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        }
        if (!Set.of("case_analysis", "case_comparison").contains(taskType) && !caseIds.isEmpty()) {
            throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        }
        if (!"case_comparison".equals(taskType) && !dimensions.isEmpty()) {
            throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        }
        if ("technology_assessment".equals(taskType)
                && technologyTagId == null && !StringUtils.hasText(technologyText)) {
            throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        }
        if (!"technology_assessment".equals(taskType)
                && (technologyTagId != null || StringUtils.hasText(technologyText))) {
            throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        }
        if (!"source_verification".equals(taskType) && sourceId != null) {
            throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        }
    }

    private List<Long> uniqueIds(JsonNode node, String field, int max) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > max) throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        List<Long> values = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isIntegralNumber() || !item.canConvertToLong()
                    || item.asLong() <= 0 || !seen.add(item.asLong())) {
                throw invalid("PHASE3_TASK_CONTEXT_INVALID");
            }
            values.add(item.asLong());
        }
        return List.copyOf(values);
    }

    private List<String> uniqueDimensions(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > 3) throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        List<String> values = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || !AgentResearchContract.COMPARISON_DIMENSIONS.contains(item.asText())
                    || !seen.add(item.asText())) {
                throw invalid("PHASE3_TASK_CONTEXT_INVALID");
            }
            values.add(item.asText());
        }
        return List.copyOf(values);
    }

    private String requiredText(JsonNode raw, String field, int max) {
        String value = optionalText(raw, field, max);
        if (!StringUtils.hasText(value)) throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        return value;
    }

    private String optionalText(JsonNode raw, String field, int max) {
        JsonNode node = raw.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        String value = node.asText().trim();
        if (value.codePointCount(0, value.length()) > max) throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        return value.isEmpty() ? null : value;
    }

    private Long optionalPositiveLong(JsonNode raw, String field) {
        JsonNode node = raw.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() <= 0) {
            throw invalid("PHASE3_TASK_CONTEXT_INVALID");
        }
        return node.asLong();
    }

    private void putOptional(ObjectNode node, String field, Long value) {
        if (value != null) node.put(field, value);
    }

    private void putOptional(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) node.put(field, value);
    }

    private String canonicalJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(sort(node));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to canonicalize task context", exception);
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isArray()) {
            ArrayNode values = objectMapper.createArrayNode();
            node.forEach(item -> values.add(sort(item)));
            return values;
        }
        if (!node.isObject()) return node.deepCopy();
        ObjectNode sorted = objectMapper.createObjectNode();
        Map<String, JsonNode> fields = new TreeMap<>();
        node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
        fields.forEach((key, value) -> sorted.set(key, sort(value)));
        return sorted;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private BusinessException invalid(String code) {
        return new BusinessException(ErrorCode.BAD_REQUEST, code);
    }
}
