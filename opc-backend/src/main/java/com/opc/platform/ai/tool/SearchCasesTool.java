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
    private final AgentRegionResolver regionResolver;
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
                  "scope":{"type":"string","enum":["selected","cross_region_reference"]},
                  "query":{"type":"string","maxLength":120},
                  "category":{"type":"string","maxLength":50},
                  "limit":{"type":"integer","minimum":1,"maximum":10}
                }}
                """;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
        int limit = arguments.getLimit() == null ? 5 : Math.max(1, Math.min(10, arguments.getLimit()));
        AgentRegionResolver.RegionScope scope = regionResolver.resolveScope(
                context.primaryRegionId(), arguments.getScope());
        List<Long> selectedRegionIds = descendantRegionIds(context.primaryRegionId());
        boolean crossRegion = "cross_region_reference".equals(scope.scope());
        List<Long> regionIds = crossRegion ? List.of() : selectedRegionIds;
        List<Long> excludedRegionIds = crossRegion ? selectedRegionIds : List.of();
        List<AgentCaseSearchRow> rows = mapper.searchCases(
                regionIds, excludedRegionIds, context.primaryIndustryTagId(), trim(context.primaryIndustry()),
                trim(arguments.getQuery()), trim(arguments.getCategory()), limit
        );
        List<CaseItem> items = (rows == null ? List.<AgentCaseSearchRow>of() : rows).stream()
                .map(row -> new CaseItem(
                        row.getCaseId(), bounded(row.getTitle(), 240), bounded(row.getRegion(), 100),
                        row.getRegionId(), bounded(row.getGeographicLevel(), 30),
                        bounded(row.getCategory(), 50), bounded(row.getSummary(), 500),
                        bounded(row.getBusinessModel(), 500), bounded(row.getOutcome(), 500),
                        row.getSourceId(), "verified", crossRegion ? "cross_region" : "exact",
                        matchReason(context, crossRegion, arguments)
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

    private List<Long> descendantRegionIds(Long regionId) {
        if (regionId == null) return List.of();
        List<Long> ids = mapper.selectDescendantRegionIds(regionId);
        if (ids == null || ids.isEmpty()) {
            throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "所选地区不存在");
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().limit(500).toList();
    }

    private String matchReason(
            AgentToolContext context,
            boolean crossRegion,
            SearchCasesArguments arguments
    ) {
        if (crossRegion) return "所选地区资料不足时使用的跨地区借鉴案例";
        if (context.primaryIndustryTagId() != null) return "匹配已确认地区和行业标签";
        if (StringUtils.hasText(arguments.getQuery())) return "匹配研究关键词";
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
            Long regionId,
            String geographicLevel,
            String category,
            String summary,
            String businessModel,
            String outcome,
            Long sourceId,
            String evidenceStatus,
            String geographicScope,
            String matchReason
    ) {
    }
}
