package com.opc.platform.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentToolCall;
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
            context.accept(current);
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
        callMapper.insert(audit);

        AgentTool<?> tool = tools.get(toolName);
        if (tool == null) {
            AgentToolException exception = new AgentToolException("UNKNOWN_TOOL", "模型请求了未授权工具");
            fail(audit, exception.getDiagnosticCode(), startedNanos);
            throw exception;
        }

        try {
            Object arguments = parseAndValidate(tool, rawArguments);
            audit.setArgumentsJson(objectMapper.writeValueAsString(arguments));
            audit.setStatus("running");
            audit.setStartedAt(LocalDateTime.now());
            callMapper.updateById(audit);
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
            callMapper.updateById(audit);
            context.accept(result);
            return new AgentToolExecution(audit.getId(), tool.name(), result);
        } catch (AgentToolException exception) {
            fail(audit, exception.getDiagnosticCode(), startedNanos);
            throw exception;
        } catch (JsonProcessingException exception) {
            fail(audit, "INVALID_TOOL_ARGUMENTS", startedNanos);
            throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "工具参数格式无效");
        } catch (BusinessException exception) {
            fail(audit, exception.getErrorCode().name(), startedNanos);
            throw exception;
        } catch (RuntimeException exception) {
            fail(audit, "TOOL_EXECUTION_FAILED", startedNanos);
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

    @SuppressWarnings("unchecked")
    private <T> AgentToolResult executeTyped(AgentTool<?> rawTool, AgentToolContext context, Object arguments) {
        return ((AgentTool<T>) rawTool).execute(context, (T) arguments);
    }

    private void fail(AiAgentToolCall audit, String diagnosticCode, long startedNanos) {
        audit.setStatus("failed");
        audit.setDiagnosticCode(diagnosticCode);
        audit.setCompletedAt(LocalDateTime.now());
        audit.setLatencyMs(elapsedMillis(startedNanos));
        callMapper.updateById(audit);
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
