package com.opc.platform.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.mapper.AgentEvidenceToolMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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
                {"type":"object","additionalProperties":false,"anyOf":[{"required":["regionId"]},{"required":["regionName"]}],"properties":{
                  "regionId":{"type":"integer"},"regionName":{"type":"string","maxLength":50},
                  "industryTagId":{"type":"integer"},
                  "industry":{"type":"string","maxLength":100},
                  "query":{"type":"string","maxLength":120},
                  "limit":{"type":"integer","minimum":1,"maximum":10}
                }}
                """;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, SearchPoliciesArguments arguments) {
        Long regionId = resolveRegion(context, arguments.getRegionId(), arguments.getRegionName());
        RegionScope regionScope = regionScope(regionId);
        int limit = arguments.getLimit() == null ? 5 : Math.max(1, Math.min(10, arguments.getLimit()));
        List<AgentPolicySearchRow> rows = mapper.searchPolicies(
                regionScope.regionIds(), arguments.getIndustryTagId(), trim(arguments.getIndustry()),
                trim(arguments.getQuery()), limit
        );
        List<PolicyItem> items = (rows == null ? List.<AgentPolicySearchRow>of() : rows).stream()
                .map(row -> new PolicyItem(
                        row.getPolicyId(), bounded(row.getTitle(), 240), bounded(row.getPolicyType(), 50),
                        bounded(row.getSummary(), 600), bounded(row.getSupportMeasures(), 600),
                        safeMode(row.getApplicabilityMode()), bounded(row.getGeographicLevel(), 30),
                        row.getRegionId(), row.getSourceId(),
                        geographicScope(context, regionId, row, regionScope), matchReason(row, arguments)
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

    private RegionScope regionScope(Long regionId) {
        Region selected = regionMapper.selectById(regionId);
        if (selected == null) throw new AgentToolException("INVALID_TOOL_ARGUMENTS", "所选地区不存在");
        List<Long> ordered = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        Set<Long> exact = new LinkedHashSet<>();
        Set<Long> parents = new LinkedHashSet<>();
        Set<Long> national = new LinkedHashSet<>();
        List<Long> descendants = mapper.selectDescendantRegionIds(regionId);
        if (descendants != null) {
            descendants.stream().filter(java.util.Objects::nonNull).limit(500).forEach(id -> {
                if (seen.add(id)) ordered.add(id);
                exact.add(id);
            });
        }
        if (seen.add(selected.getId())) ordered.add(selected.getId());
        exact.add(selected.getId());
        Long currentId = selected.getParentId();
        int ancestorDepth = 0;
        while (currentId != null && seen.add(currentId) && ancestorDepth++ < 16) {
            Region current = regionMapper.selectById(currentId);
            if (current == null) break;
            ordered.add(current.getId());
            if ("country".equals(current.getLevel())) national.add(current.getId());
            else parents.add(current.getId());
            currentId = current.getParentId();
        }
        return new RegionScope(List.copyOf(ordered), Set.copyOf(exact), Set.copyOf(parents), Set.copyOf(national));
    }

    private String geographicScope(
            AgentToolContext context,
            Long requestedRegionId,
            AgentPolicySearchRow row,
            RegionScope scope
    ) {
        if (scope.national().contains(row.getRegionId())) return "national";
        if (context.primaryRegionId() != null && !context.primaryRegionId().equals(requestedRegionId)) {
            return "cross_region";
        }
        if (scope.parents().contains(row.getRegionId())) return "parent";
        return "exact";
    }

    private String matchReason(AgentPolicySearchRow row, SearchPoliciesArguments arguments) {
        return switch (safeMode(row.getApplicabilityMode())) {
            case "specific" -> arguments.getIndustryTagId() != null
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

    private record RegionScope(
            List<Long> regionIds,
            Set<Long> exact,
            Set<Long> parents,
            Set<Long> national
    ) {
    }
}
