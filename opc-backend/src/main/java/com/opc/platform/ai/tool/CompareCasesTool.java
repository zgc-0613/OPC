package com.opc.platform.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.ai.mapper.AgentEvidenceToolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CompareCasesTool implements AgentTool<CompareCasesArguments> {

    private static final List<String> DEFAULT_DIMENSIONS = AgentResearchContract.COMPARISON_DIMENSIONS;
    private static final Set<String> ALLOWED_DIMENSIONS = Set.copyOf(
            AgentResearchContract.COMPARISON_DIMENSIONS);

    private final AgentEvidenceToolMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "compare_cases";
    }

    @Override
    public String description() {
        return "按预定义维度确定性比较当前运行已检索到的二至三个案例";
    }

    @Override
    public Class<CompareCasesArguments> argumentType() {
        return CompareCasesArguments.class;
    }

    @Override
    public String argumentSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["caseIds"],"properties":{
                  "caseIds":{"type":"array","minItems":2,"maxItems":3,"uniqueItems":true,"items":{"type":"integer"}},
                  "dimensions":{"type":"array","maxItems":6,"uniqueItems":true,"items":{
                    "type":"string","enum":["businessModel","technicalPath","targetCustomer","outcome","regionalContext","evidenceStrength"]
                  }}
                }}
                """;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, CompareCasesArguments arguments) {
        List<Long> caseIds = arguments.getCaseIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (caseIds.size() != arguments.getCaseIds().size()) {
            throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "比较案例不能重复");
        }
        if (!context.allowedCaseIds().containsAll(caseIds)) {
            throw new AgentToolException("FORBIDDEN_CASE_ID", "案例不属于当前运行的检索结果");
        }
        List<String> dimensions = arguments.getDimensions() == null || arguments.getDimensions().isEmpty()
                ? DEFAULT_DIMENSIONS
                : arguments.getDimensions().stream().distinct().toList();
        if ((arguments.getDimensions() != null && dimensions.size() != arguments.getDimensions().size())
                || !ALLOWED_DIMENSIONS.containsAll(dimensions)) {
            throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "包含不允许的案例比较维度");
        }
        List<AgentCaseSearchRow> rows = mapper.loadCases(caseIds);
        if (rows == null || rows.size() != caseIds.size()) {
            throw new AgentToolException(com.opc.platform.common.enums.ErrorCode.CONFLICT,
                    "EVIDENCE_CHANGED", "案例证据状态已变化，请重新检索");
        }
        List<CaseSummary> cases = rows.stream().map(row -> new CaseSummary(
                row.getCaseId(), bounded(row.getTitle(), 240), row.getSourceId()
        )).toList();
        List<Conclusion> conclusions = new ArrayList<>();
        for (String dimension : dimensions) {
            for (AgentCaseSearchRow row : rows) {
                conclusions.add(new Conclusion(
                        dimension,
                        row.getCaseId(),
                        valueFor(row, dimension),
                        row.getSourceId()
                ));
            }
        }
        var output = objectMapper.createObjectNode();
        output.set("cases", objectMapper.valueToTree(cases));
        output.set("conclusions", objectMapper.valueToTree(conclusions));
        Set<Long> sourceIds = new LinkedHashSet<>();
        rows.forEach(row -> sourceIds.add(row.getSourceId()));
        return new AgentToolResult(
                output,
                conclusions.size(),
                AgentEvidenceHasher.hash(objectMapper, rows),
                sourceIds,
                new LinkedHashSet<>(caseIds)
        );
    }

    private String valueFor(AgentCaseSearchRow row, String dimension) {
        return switch (dimension) {
            case "businessModel" -> bounded(row.getBusinessModel(), 500);
            case "technicalPath" -> bounded(row.getAiTools(), 500);
            case "targetCustomer" -> bounded(row.getSummary(), 500);
            case "outcome" -> bounded(row.getOutcome(), 500);
            case "regionalContext" -> bounded(row.getRegion(), 120);
            case "evidenceStrength" -> "案例与来源均为已发布且已核验状态";
            default -> throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "不允许的案例比较维度");
        };
    }

    private String bounded(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private record CaseSummary(Long caseId, String title, Long sourceId) {
    }

    private record Conclusion(String dimension, Long caseId, String statement, Long sourceId) {
    }
}
