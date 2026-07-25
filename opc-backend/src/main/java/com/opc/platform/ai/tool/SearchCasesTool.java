package com.opc.platform.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.mapper.AgentEvidenceToolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SearchCasesTool implements AgentTool<SearchCasesArguments> {

    private final AgentEvidenceToolMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "search_cases";
    }

    @Override
    public String description() {
        return "搜索已发布、已核验且来源链合格的本地创业案例";
    }

    @Override
    public Class<SearchCasesArguments> argumentType() {
        return SearchCasesArguments.class;
    }

    @Override
    public String argumentSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{
                  "regionId":{"type":"integer"},"industryTagId":{"type":"integer"},
                  "industry":{"type":"string","maxLength":100},
                  "keywords":{"type":"string","maxLength":120},
                  "category":{"type":"string","maxLength":50},
                  "limit":{"type":"integer","minimum":1,"maximum":10}
                }}
                """;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
        int limit = arguments.getLimit() == null ? 5 : Math.max(1, Math.min(10, arguments.getLimit()));
        List<AgentCaseSearchRow> rows = mapper.searchCases(
                arguments.getRegionId(), arguments.getIndustryTagId(), trim(arguments.getIndustry()),
                trim(arguments.getKeywords()), trim(arguments.getCategory()), limit
        );
        List<CaseItem> items = (rows == null ? List.<AgentCaseSearchRow>of() : rows).stream()
                .map(row -> new CaseItem(
                        row.getCaseId(), bounded(row.getTitle(), 240), bounded(row.getRegion(), 100),
                        bounded(row.getCategory(), 50), bounded(row.getSummary(), 500),
                        bounded(row.getBusinessModel(), 500), bounded(row.getOutcome(), 500),
                        row.getSourceId(), "verified", matchReason(arguments)
                )).toList();
        Set<Long> sources = new LinkedHashSet<>();
        Set<Long> cases = new LinkedHashSet<>();
        items.forEach(item -> {
            sources.add(item.sourceId());
            cases.add(item.caseId());
        });
        var output = objectMapper.createObjectNode();
        output.set("items", objectMapper.valueToTree(items));
        return new AgentToolResult(
                output,
                items.size(),
                AgentEvidenceHasher.hash(objectMapper, rows == null ? List.of() : rows),
                sources,
                cases
        );
    }

    private String matchReason(SearchCasesArguments arguments) {
        if (arguments.getIndustryTagId() != null) return "匹配已核验行业标签";
        if (arguments.getRegionId() != null) return "匹配所选地区";
        if (StringUtils.hasText(arguments.getKeywords())) return "匹配研究关键词";
        return "符合已发布和已核验证据条件";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String bounded(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private record CaseItem(
            Long caseId,
            String title,
            String region,
            String category,
            String summary,
            String businessModel,
            String outcome,
            Long sourceId,
            String evidenceStatus,
            String matchReason
    ) {
    }
}
