package com.opc.platform.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.provider.AiToolDefinition;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgentToolRegistry {

    private static final int MAX_ARGUMENT_JSON_LENGTH = 4000;
    private static final int MAX_RESULT_JSON_LENGTH = 16000;

    private final Map<String, AgentTool<?>> tools;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AiAgentToolCallMapper callMapper;

    public AgentToolRegistry(
            List<AgentTool<?>> toolList,
            ObjectMapper objectMapper,
            Validator validator,
            AiAgentToolCallMapper callMapper
    ) {
        this.tools = new LinkedHashMap<>();
        for (AgentTool<?> tool : toolList) {
            if (tools.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("Duplicate Agent tool: " + tool.name());
            }
        }
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.callMapper = callMapper;
    }

    public List<AgentTool<?>> tools() {
        return List.copyOf(tools.values());
    }

    public List<AiToolDefinition> definitions() {
        return tools.values().stream()
                .map(tool -> new AiToolDefinition(tool.name(), tool.description(), tool.argumentSchema()))
                .toList();
    }

    public String promptCatalog() {
        StringBuilder catalog = new StringBuilder();
        for (AgentTool<?> tool : tools.values()) {
            catalog.append("- ").append(tool.name()).append(": ")
                    .append(tool.description()).append("; arguments=")
                    .append(compactSchema(tool)).append('\n');
        }
        return catalog.toString();
    }

    public String jsonPlanSchema() {
        var root = objectMapper.createObjectNode();
        var branches = root.putArray("oneOf");
        for (AgentTool<?> tool : tools.values()) {
            var branch = branches.addObject();
            branch.put("type", "object");
            branch.put("additionalProperties", false);
            branch.putArray("required").add("action").add("toolName").add("arguments");
            var properties = branch.putObject("properties");
            properties.putObject("action").put("const", "tool");
            properties.putObject("toolName").put("const", tool.name());
            properties.set("arguments", parseSchema(tool));
        }
        addFinalBranch(branches, "final");
        addFinalBranch(branches, "evidence_insufficient");
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize Agent tool plan schema", exception);
        }
    }

    public String jsonResearchSchemaV2() {
        var root = objectMapper.createObjectNode();
        var branches = root.putArray("oneOf");
        addResearchPlanBranch(branches);
        addResearchFinalBranch(branches, "final");
        addResearchFinalBranch(branches, "evidence_insufficient");
        return serializeSchema(root);
    }

    public String jsonResearchPlanSchemaV2() {
        var root = objectMapper.createObjectNode();
        addResearchPlanBranch(root.putArray("oneOf"));
        return serializeSchema(root);
    }

    public String jsonCompactResearchPlanSchemaV2() {
        var root = objectMapper.createObjectNode();
        root.put("type", "object").put("additionalProperties", false);
        root.putArray("required").add("action").add("intent").add("researchQuestions")
                .add("toolRequests").add("comparisonDimensions").add("outputSections");
        var properties = root.putObject("properties");
        properties.putObject("action").put("const", "plan");
        addIntentSchema(properties.putObject("intent"));
        addStringArray(properties.putObject("researchQuestions"), 1,
                AgentResearchContract.MAX_RESEARCH_QUESTIONS,
                AgentResearchContract.MAX_RESEARCH_QUESTION_LENGTH);

        var requests = properties.putObject("toolRequests");
        requests.put("type", "array").put("minItems", 1)
                .put("maxItems", AgentResearchContract.MAX_PLANNED_TOOLS);
        var request = requests.putObject("items");
        request.put("type", "object").put("additionalProperties", false);
        request.putArray("required").add("requestId").add("toolName").add("arguments").add("dependsOn");
        var requestProperties = request.putObject("properties");
        requestProperties.putObject("requestId").put("type", "string")
                .put("pattern", "^[A-Za-z][A-Za-z0-9_-]{0,31}$");
        var toolNames = requestProperties.putObject("toolName");
        toolNames.put("type", "string");
        tools.keySet().forEach(toolNames.putArray("enum")::add);
        var arguments = requestProperties.putObject("arguments");
        arguments.put("type", "object").put("additionalProperties", false).put("maxProperties", 8);
        var argumentProperties = arguments.putObject("properties");
        for (AgentTool<?> tool : tools.values()) {
            parseSchema(tool).path("properties").fields().forEachRemaining(field -> {
                if (!argumentProperties.has(field.getKey())) {
                    argumentProperties.set(field.getKey(), field.getValue().deepCopy());
                }
            });
        }
        addStringArray(requestProperties.putObject("dependsOn"), 0,
                AgentResearchContract.MAX_DEPENDENCIES,
                AgentResearchContract.MAX_DEPENDENCY_LENGTH);
        addStringArray(properties.putObject("comparisonDimensions"), 0,
                AgentResearchContract.MAX_COMPARISON_DIMENSIONS,
                AgentResearchContract.MAX_COMPARISON_DIMENSION_LENGTH);
        var outputSections = properties.putObject("outputSections");
        outputSections.put("type", "array").put("minItems", 2)
                .put("maxItems", AgentResearchContract.OUTPUT_SECTIONS.size()).put("uniqueItems", true);
        var section = outputSections.putObject("items");
        section.put("type", "string");
        var sections = section.putArray("enum");
        AgentResearchContract.OUTPUT_SECTIONS.forEach(sections::add);
        return serializeSchema(root);
    }

    public String jsonCompactResearchFinalSchemaV2() {
        var root = objectMapper.createObjectNode();
        var branches = root.putArray("oneOf");
        addResearchFinalBranch(branches, "final");
        addResearchFinalBranch(branches, "evidence_insufficient");
        return serializeSchema(root);
    }

    public String jsonResearchFinalSchemaV2() {
        var root = objectMapper.createObjectNode();
        var branches = root.putArray("oneOf");
        addResearchFinalBranch(branches, "final");
        addResearchFinalBranch(branches, "evidence_insufficient");
        return serializeSchema(root);
    }

    private String serializeSchema(JsonNode root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize Agent research schema", exception);
        }
    }

    public boolean contains(String toolName) {
        return tools.containsKey(toolName);
    }

    public void verifyEvidence(
            AgentToolContext context,
            String toolName,
            JsonNode argumentsNode,
            String expectedEvidenceHash
    ) {
        AgentTool<?> tool = tools.get(toolName);
        if (tool == null) {
            throw new AgentToolException("UNKNOWN_TOOL", "运行记录包含未知工具");
        }
        try {
            Object arguments = parseAndValidate(tool, argumentsNode);
            AgentToolResult current = executeTyped(tool, context, arguments);
            if (expectedEvidenceHash == null || !expectedEvidenceHash.equals(current.evidenceHash())) {
                throw new AgentToolException(ErrorCode.CONFLICT,
                        "EVIDENCE_CHANGED", "研究期间证据已变化，请重新提交");
            }
            context.accept(toolName, current);
        } catch (JsonProcessingException exception) {
            throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "运行记录中的工具参数无效");
        }
    }

    public AgentToolExecution execute(
            AgentToolContext context,
            int stepNo,
            String toolName,
            JsonNode rawArguments
    ) {
        long startedNanos = System.nanoTime();
        AiAgentToolCall audit = new AiAgentToolCall();
        audit.setAnalysisRunId(context.runId());
        audit.setStepNo(stepNo);
        audit.setToolName(safeToolName(toolName));
        audit.setArgumentsJson(safeRawArguments(rawArguments));
        audit.setStatus("pending");
        audit.setEvidenceCount(0);
        audit.setLatencyMs(0L);
        if (callMapper.insertGuarded(audit, context.leaseOwner()) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Agent tool write was rejected because the run lease or session content changed");
        }

        AgentTool<?> tool = tools.get(toolName);
        if (tool == null) {
            AgentToolException exception = new AgentToolException("UNKNOWN_TOOL", "模型请求了未授权工具");
            fail(context, audit, exception.getDiagnosticCode(), startedNanos);
            throw exception;
        }

        try {
            Object arguments = parseAndValidate(tool, rawArguments);
            audit.setArgumentsJson(objectMapper.writeValueAsString(arguments));
            audit.setStatus("running");
            audit.setStartedAt(LocalDateTime.now());
            requireGuardedUpdate(context, audit);
            AgentToolResult result = executeTyped(tool, context, arguments);
            String resultJson = objectMapper.writeValueAsString(result.output());
            if (resultJson.length() > MAX_RESULT_JSON_LENGTH) {
                throw new AgentToolException("TOOL_RESULT_TOO_LARGE", "工具结果超过安全上限");
            }
            audit.setResultSummaryJson(resultJson);
            audit.setEvidenceHash(result.evidenceHash());
            audit.setEvidenceCount(Math.max(0, result.evidenceCount()));
            audit.setStatus("completed");
            audit.setCompletedAt(LocalDateTime.now());
            audit.setLatencyMs(elapsedMillis(startedNanos));
            requireGuardedUpdate(context, audit);
            context.accept(toolName, result);
            return new AgentToolExecution(audit.getId(), tool.name(), result);
        } catch (AgentToolException exception) {
            fail(context, audit, exception.getDiagnosticCode(), startedNanos);
            throw exception;
        } catch (JsonProcessingException exception) {
            fail(context, audit, "INVALID_TOOL_ARGUMENTS", startedNanos);
            throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "工具参数格式无效");
        } catch (BusinessException exception) {
            fail(context, audit, exception.getErrorCode().name(), startedNanos);
            throw exception;
        } catch (RuntimeException exception) {
            fail(context, audit, "TOOL_EXECUTION_FAILED", startedNanos);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "工具执行失败");
        }
    }

    private Object parseAndValidate(AgentTool<?> tool, JsonNode rawArguments) throws JsonProcessingException {
        if (rawArguments == null || !rawArguments.isObject()
                || rawArguments.toString().length() > MAX_ARGUMENT_JSON_LENGTH) {
            throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "工具参数格式无效");
        }
        Object arguments = objectMapper.readerFor(tool.argumentType())
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(rawArguments.toString());
        Set<ConstraintViolation<Object>> violations = validator.validate(arguments);
        if (!violations.isEmpty()) {
            throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "工具参数未通过校验");
        }
        return arguments;
    }

    private JsonNode parseSchema(AgentTool<?> tool) {
        try {
            JsonNode schema = objectMapper.readTree(tool.argumentSchema());
            if (schema == null || !schema.isObject()
                    || !"object".equals(schema.path("type").asText())
                    || !schema.path("additionalProperties").isBoolean()
                    || schema.path("additionalProperties").asBoolean()) {
                throw new IllegalStateException("Agent tool schema must be a closed object: " + tool.name());
            }
            return schema;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid Agent tool schema: " + tool.name(), exception);
        }
    }

    private String compactSchema(AgentTool<?> tool) {
        return parseSchema(tool).toString();
    }

    private void addFinalBranch(com.fasterxml.jackson.databind.node.ArrayNode branches, String action) {
        var branch = branches.addObject();
        branch.put("type", "object");
        branch.put("additionalProperties", false);
        branch.putArray("required").add("action").add("answer").add("citations").add("confidence");
        var properties = branch.putObject("properties");
        properties.putObject("action").put("const", action);
        properties.putObject("answer").put("type", "string").put("minLength", 1).put("maxLength", 12000);
        var citations = properties.putObject("citations");
        citations.put("type", "array").put("maxItems", 12);
        var citation = citations.putObject("items");
        citation.put("type", "object").put("additionalProperties", false);
        citation.putArray("required").add("sourceId").add("claim");
        var citationProperties = citation.putObject("properties");
        citationProperties.putObject("sourceId").put("type", "integer");
        citationProperties.putObject("claim").put("type", "string").put("minLength", 1).put("maxLength", 300);
        properties.putObject("confidence").put("type", "number").put("minimum", 0).put("maximum", 1);
    }

    private void addResearchPlanBranch(com.fasterxml.jackson.databind.node.ArrayNode branches) {
        var branch = branches.addObject();
        branch.put("type", "object");
        branch.put("additionalProperties", false);
        branch.putArray("required").add("action").add("intent").add("researchQuestions")
                .add("toolRequests").add("comparisonDimensions").add("outputSections");
        var properties = branch.putObject("properties");
        properties.putObject("action").put("const", "plan");
        addIntentSchema(properties.putObject("intent"));
        addStringArray(properties.putObject("researchQuestions"), 1,
                AgentResearchContract.MAX_RESEARCH_QUESTIONS,
                AgentResearchContract.MAX_RESEARCH_QUESTION_LENGTH);
        var requests = properties.putObject("toolRequests");
        requests.put("type", "array").put("minItems", 1)
                .put("maxItems", AgentResearchContract.MAX_PLANNED_TOOLS);
        var requestBranches = requests.putObject("items").putArray("oneOf");
        for (AgentTool<?> tool : tools.values()) {
            var request = requestBranches.addObject();
            request.put("type", "object").put("additionalProperties", false);
            request.putArray("required").add("requestId").add("toolName").add("arguments").add("dependsOn");
            var requestProperties = request.putObject("properties");
            requestProperties.putObject("requestId").put("type", "string")
                    .put("pattern", "^[A-Za-z][A-Za-z0-9_-]{0,31}$");
            requestProperties.putObject("toolName").put("const", tool.name());
            requestProperties.set("arguments", parseSchema(tool));
            addStringArray(requestProperties.putObject("dependsOn"), 0,
                    AgentResearchContract.MAX_DEPENDENCIES,
                    AgentResearchContract.MAX_DEPENDENCY_LENGTH);
        }
        addStringArray(properties.putObject("comparisonDimensions"), 0,
                AgentResearchContract.MAX_COMPARISON_DIMENSIONS,
                AgentResearchContract.MAX_COMPARISON_DIMENSION_LENGTH);
        var outputSections = properties.putObject("outputSections");
        outputSections.put("type", "array").put("minItems", 2)
                .put("maxItems", AgentResearchContract.OUTPUT_SECTIONS.size()).put("uniqueItems", true);
        var sectionEnum = outputSections.putObject("items").putArray("enum");
        for (String section : AgentResearchContract.OUTPUT_SECTIONS) sectionEnum.add(section);
        ((com.fasterxml.jackson.databind.node.ObjectNode) outputSections.get("items")).put("type", "string");
    }

    private void addResearchFinalBranch(
            com.fasterxml.jackson.databind.node.ArrayNode branches,
            String action
    ) {
        var branch = branches.addObject();
        branch.put("type", "object").put("additionalProperties", false);
        var required = branch.putArray("required");
        for (String field : List.of(
                "action", "intent", "directAnswer", "keyFindings", "caseInsights", "policyInsights",
                "comparison", "recommendations", "risks", "assumptions", "uncertainties",
                "nextQuestions", "citations", "confidence", "evidenceCoverage"
        )) required.add(field);
        var properties = branch.putObject("properties");
        properties.putObject("action").put("const", action);
        addIntentSchema(properties.putObject("intent"));
        properties.putObject("directAnswer").put("type", "string").put("minLength", 1)
                .put("maxLength", AgentResearchContract.MAX_DIRECT_ANSWER_LENGTH);
        addEvidenceStatementArray(properties.putObject("keyFindings"), AgentResearchContract.MAX_KEY_FINDINGS);
        addEvidenceStatementArray(properties.putObject("caseInsights"), AgentResearchContract.MAX_CASE_INSIGHTS);
        addEvidenceStatementArray(properties.putObject("policyInsights"), AgentResearchContract.MAX_POLICY_INSIGHTS);
        addEvidenceStatementArray(properties.putObject("comparison"), AgentResearchContract.MAX_COMPARISON_ITEMS);
        var recommendations = properties.putObject("recommendations");
        recommendations.put("type", "array").put("maxItems", AgentResearchContract.MAX_RECOMMENDATIONS);
        var recommendation = recommendations.putObject("items");
        recommendation.put("type", "object").put("additionalProperties", false);
        recommendation.putArray("required").add("priority").add("reason").add("nextAction").add("sourceIds");
        var recommendationProperties = recommendation.putObject("properties");
        recommendationProperties.putObject("priority").put("type", "string")
                .putArray("enum").add("high").add("medium").add("low");
        recommendationProperties.putObject("reason").put("type", "string").put("minLength", 1)
                .put("maxLength", AgentResearchContract.MAX_RECOMMENDATION_FIELD_LENGTH);
        recommendationProperties.putObject("nextAction").put("type", "string").put("minLength", 1)
                .put("maxLength", AgentResearchContract.MAX_RECOMMENDATION_FIELD_LENGTH);
        addIdArray(recommendationProperties.putObject("sourceIds"), 1,
                AgentResearchContract.MAX_SOURCE_IDS_PER_ITEM);
        addStringArray(properties.putObject("risks"), 0, AgentResearchContract.MAX_RISKS,
                AgentResearchContract.MAX_SUPPLEMENTAL_ITEM_LENGTH);
        addStringArray(properties.putObject("assumptions"), 0, AgentResearchContract.MAX_ASSUMPTIONS,
                AgentResearchContract.MAX_SUPPLEMENTAL_ITEM_LENGTH);
        addStringArray(properties.putObject("uncertainties"), 0, AgentResearchContract.MAX_UNCERTAINTIES,
                AgentResearchContract.MAX_SUPPLEMENTAL_ITEM_LENGTH);
        addStringArray(properties.putObject("nextQuestions"), 0, AgentResearchContract.MAX_NEXT_QUESTIONS,
                AgentResearchContract.MAX_SUPPLEMENTAL_ITEM_LENGTH);
        var citations = properties.putObject("citations");
        boolean requiresCitations = "final".equals(action);
        citations.put("type", "array").put("minItems", requiresCitations ? 1 : 0)
                .put("maxItems", requiresCitations ? AgentResearchContract.MAX_CITATIONS : 0);
        var citation = citations.putObject("items");
        citation.put("type", "object").put("additionalProperties", false);
        citation.putArray("required").add("sourceId").add("claim");
        var citationProperties = citation.putObject("properties");
        citationProperties.putObject("sourceId").put("type", "integer");
        citationProperties.putObject("claim").put("type", "string").put("minLength", 1)
                .put("maxLength", AgentResearchContract.MAX_CITATION_CLAIM_LENGTH);
        properties.putObject("confidence").put("type", "number").put("minimum", 0).put("maximum", 1);
        var coverage = properties.putObject("evidenceCoverage");
        coverage.put("type", "object").put("additionalProperties", false);
        coverage.putArray("required").add("status").add("caseCount").add("policyCount")
                .add("sourceCount").add("limitations");
        var coverageProperties = coverage.putObject("properties");
        coverageProperties.putObject("status").put("type", "string")
                .putArray("enum").add("sufficient").add("partial").add("insufficient");
        for (String count : List.of("caseCount", "policyCount", "sourceCount")) {
            coverageProperties.putObject(count).put("type", "integer").put("minimum", 0);
        }
        addStringArray(coverageProperties.putObject("limitations"), 0,
                AgentResearchContract.MAX_COVERAGE_LIMITATIONS,
                AgentResearchContract.MAX_COVERAGE_LIMITATION_LENGTH);
    }

    private void addIntentSchema(com.fasterxml.jackson.databind.node.ObjectNode node) {
        node.put("type", "string");
        var values = node.putArray("enum");
        for (String value : AgentResearchContract.INTENTS) values.add(value);
    }

    private void addEvidenceStatementArray(
            com.fasterxml.jackson.databind.node.ObjectNode array,
            int maxItems
    ) {
        array.put("type", "array").put("maxItems", maxItems);
        var branches = array.putObject("items").putArray("oneOf");
        addEvidenceStatementBranch(branches, "fact", 1);
        addEvidenceStatementBranch(branches, "inference", 0);
        addEvidenceStatementBranch(branches, "methodology", 0);
    }

    private void addEvidenceStatementBranch(
            com.fasterxml.jackson.databind.node.ArrayNode branches,
            String evidenceType,
            int minimumSourceIds
    ) {
        var item = branches.addObject();
        item.put("type", "object").put("additionalProperties", false);
        item.putArray("required").add("text").add("evidenceType").add("sourceIds");
        var properties = item.putObject("properties");
        properties.putObject("text").put("type", "string").put("minLength", 1)
                .put("maxLength", AgentResearchContract.MAX_STATEMENT_LENGTH);
        properties.putObject("evidenceType").put("const", evidenceType);
        addIdArray(properties.putObject("sourceIds"), minimumSourceIds,
                AgentResearchContract.MAX_SOURCE_IDS_PER_ITEM);
    }

    private void addStringArray(
            com.fasterxml.jackson.databind.node.ObjectNode array,
            int minItems,
            int maxItems,
            int maxLength
    ) {
        array.put("type", "array").put("minItems", minItems).put("maxItems", maxItems);
        array.putObject("items").put("type", "string").put("minLength", 1).put("maxLength", maxLength);
    }

    private void addIdArray(com.fasterxml.jackson.databind.node.ObjectNode array, int minItems, int maxItems) {
        array.put("type", "array").put("minItems", minItems).put("maxItems", maxItems).put("uniqueItems", true);
        array.putObject("items").put("type", "integer");
    }

    @SuppressWarnings("unchecked")
    private <T> AgentToolResult executeTyped(AgentTool<?> rawTool, AgentToolContext context, Object arguments) {
        return ((AgentTool<T>) rawTool).execute(context, (T) arguments);
    }

    private void requireGuardedUpdate(AgentToolContext context, AiAgentToolCall audit) {
        if (callMapper.updateGuarded(audit, context.leaseOwner()) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Agent tool write was rejected because the run lease or session content changed");
        }
    }

    private void fail(
            AgentToolContext context, AiAgentToolCall audit, String diagnosticCode, long startedNanos
    ) {
        audit.setStatus("failed");
        audit.setDiagnosticCode(diagnosticCode);
        audit.setCompletedAt(LocalDateTime.now());
        audit.setLatencyMs(elapsedMillis(startedNanos));
        callMapper.updateGuarded(audit, context.leaseOwner());
    }

    private String safeRawArguments(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) return "{}";
        String value = arguments.toString();
        return value.length() <= MAX_ARGUMENT_JSON_LENGTH ? value : "{}";
    }

    private String safeToolName(String value) {
        if (value == null) return "unknown";
        String trimmed = value.trim();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60);
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
