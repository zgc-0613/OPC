package com.opc.platform.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
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
        Object arguments = objectMapper.treeToValue(rawArguments, tool.argumentType());
        Set<ConstraintViolation<Object>> violations = validator.validate(arguments);
        if (!violations.isEmpty()) {
            throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "工具参数未通过校验");
        }
        return arguments;
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
