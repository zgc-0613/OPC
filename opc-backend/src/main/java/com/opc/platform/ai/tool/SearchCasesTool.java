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
                  "regionId":{"type":"integer"},"regionName":{"type":"string","maxLength":50},
                  "industryTagId":{"type":"integer"},
                  "industry":{"type":"string","maxLength":100},
                  "query":{"type":"string","maxLength":120},
                  "category":{"type":"string","maxLength":50},
                  "limit":{"type":"integer","minimum":1,"maximum":10}
                }}
                """;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
        int limit = arguments.getLimit() == null ? 5 : Math.max(1, Math.min(10, arguments.getLimit()));
        Long regionId = resolveRegion(context, arguments.getRegionId(), arguments.getRegionName());
        List<Long> regionIds = descendantRegionIds(regionId);
        List<AgentCaseSearchRow> rows = mapper.searchCases(
                regionIds, arguments.getIndustryTagId(), trim(arguments.getIndustry()),
                trim(arguments.getQuery()), trim(arguments.getCategory()), limit
        );
        List<CaseItem> items = (rows == null ? List.<AgentCaseSearchRow>of() : rows).stream()
                .map(row -> new CaseItem(
                        row.getCaseId(), bounded(row.getTitle(), 240), bounded(row.getRegion(), 100),
                        row.getRegionId(), bounded(row.getGeographicLevel(), 30),
                        bounded(row.getCategory(), 50), bounded(row.getSummary(), 500),
                        bounded(row.getBusinessModel(), 500), bounded(row.getOutcome(), 500),
                        row.getSourceId(), "verified", geographicScope(context, regionId), matchReason(arguments)
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

    private Long resolveRegion(AgentToolContext context, Long regionId, String regionName) {
        if (StringUtils.hasText(regionName)) {
            AgentRegionMatch match = regionResolver.resolve(regionName);
            if (regionId != null && !regionId.equals(match.regionId())) {
                throw new AgentToolException("REGION_ARGUMENT_CONFLICT", "地区名称与编号不一致");
            }
            context.authorizeRegion(match.regionId());
            regionId = match.regionId();
        }
        if (regionId == null) regionId = context.primaryRegionId();
        context.requireRegionAuthorized(regionId);
        return regionId;
    }

    private List<Long> descendantRegionIds(Long regionId) {
        if (regionId == null) return List.of();
        List<Long> ids = mapper.selectDescendantRegionIds(regionId);
        if (ids == null || ids.isEmpty()) {
            throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "所选地区不存在");
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().limit(500).toList();
    }

    private String matchReason(SearchCasesArguments arguments) {
        if (arguments.getIndustryTagId() != null) return "匹配已核验行业标签";
        if (arguments.getRegionId() != null) return "匹配所选地区";
        if (StringUtils.hasText(arguments.getQuery())) return "匹配研究关键词";
        return "符合已发布和已核验证据条件";
    }

    private String geographicScope(AgentToolContext context, Long requestedRegionId) {
        return context.primaryRegionId() != null && !context.primaryRegionId().equals(requestedRegionId)
                ? "cross_region" : "exact";
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
