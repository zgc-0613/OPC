package com.opc.platform.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.mapper.AgentEvidenceToolMapper;
import com.opc.platform.region.mapper.RegionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SearchPoliciesTool implements AgentTool<SearchPoliciesArguments> {

    private final AgentEvidenceToolMapper mapper;
    private final RegionMapper regionMapper;
    private final AgentRegionResolver regionResolver;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "search_policies";
    }

    @Override
    public String description() {
        return "搜索所选地区及上级地区已发布、已核验的创业政策";
    }

    @Override
    public Class<SearchPoliciesArguments> argumentType() {
        return SearchPoliciesArguments.class;
    }

    @Override
    public String argumentSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{
                  "scope":{"type":"string","enum":["selected","parent","national"]},
                  "query":{"type":"string","maxLength":120},
                  "limit":{"type":"integer","minimum":1,"maximum":10}
                }}
                """;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, SearchPoliciesArguments arguments) {
        AgentRegionResolver.RegionScope requestedScope = regionResolver.resolveScope(
                context.primaryRegionId(), arguments.getScope());
        List<Long> regionIds = "selected".equals(requestedScope.scope())
                ? descendantRegionIds(context.primaryRegionId()) : requestedScope.regionIds();
        int limit = arguments.getLimit() == null ? 5 : Math.max(1, Math.min(10, arguments.getLimit()));
        List<AgentPolicySearchRow> rows = mapper.searchPolicies(
                regionIds, context.primaryIndustryTagId(), trim(context.primaryIndustry()),
                trim(arguments.getQuery()), limit
        );
        List<PolicyItem> items = (rows == null ? List.<AgentPolicySearchRow>of() : rows).stream()
                .map(row -> new PolicyItem(
                        row.getPolicyId(), bounded(row.getTitle(), 240), bounded(row.getPolicyType(), 50),
                        bounded(row.getSummary(), 600), bounded(row.getSupportMeasures(), 600),
                        safeMode(row.getApplicabilityMode()), bounded(row.getGeographicLevel(), 30),
                        row.getRegionId(), row.getSourceId(), geographicScope(requestedScope.scope()),
                        matchReason(row, context)
                )).toList();
        Set<Long> sources = new LinkedHashSet<>();
        items.forEach(item -> sources.add(item.sourceId()));
        var output = objectMapper.createObjectNode();
        output.set("items", objectMapper.valueToTree(items));
        return new AgentToolResult(
                output,
                items.size(),
                AgentEvidenceHasher.hash(objectMapper, rows == null ? List.of() : rows),
                sources,
                Set.of()
        );
    }

    private List<Long> descendantRegionIds(Long regionId) {
        List<Long> ids = mapper.selectDescendantRegionIds(regionId);
        if (ids == null || ids.isEmpty()) {
            throw new AgentToolException("INVALID_REGION_ID", "研究画像中的地区不存在");
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().limit(500).toList();
    }

    private String geographicScope(String requestedScope) {
        return switch (requestedScope) {
            case "parent" -> "parent";
            case "national" -> "national";
            default -> "exact";
        };
    }

    private String matchReason(AgentPolicySearchRow row, AgentToolContext context) {
        return switch (safeMode(row.getApplicabilityMode())) {
            case "specific" -> context.primaryIndustryTagId() != null
                    ? "匹配所选地区及已核验行业标签" : "地区内指定行业政策参考";
            case "general" -> "所选地区或上级地区通用创业政策";
            default -> "所选地区或上级地区政策参考，行业适用性未分类";
        };
    }

    private String safeMode(String value) {
        return Set.of("specific", "general", "unclassified").contains(value) ? value : "unclassified";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String bounded(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private record PolicyItem(
            Long policyId,
            String title,
            String policyType,
            String summary,
            String supportMeasures,
            String applicabilityMode,
            String geographicLevel,
            Long regionId,
            Long sourceId,
            String geographicScope,
            String matchReason
    ) {
    }

}
