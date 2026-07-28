package com.opc.platform.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentRegionResolver {

    private final RegionMapper regionMapper;

    public AgentRegionMatch resolve(String value) {
        String name = value == null ? "" : value.trim();
        if (!StringUtils.hasText(name) || name.length() > 50 || name.matches("[0-9]+")) {
            throw new AgentToolException("INVALID_REGION_NAME", "地区名称格式无效");
        }
        List<Region> matches = regionMapper.selectList(
                new LambdaQueryWrapper<Region>()
                        .like(Region::getName, name)
                        .orderByAsc(Region::getSortOrder)
                        .orderByAsc(Region::getId)
                        .last("LIMIT 10")
        );
        List<Region> exact = safe(matches).stream().filter(region -> name.equals(region.getName())).toList();
        if (exact.size() == 1) return toMatch(exact.get(0), "exact_name");
        if (exact.size() > 1 || safe(matches).size() > 1) {
            throw new AgentToolException("AMBIGUOUS_REGION", "地区名称对应多个目录项，请使用完整名称");
        }
        if (safe(matches).isEmpty()) {
            throw new AgentToolException("REGION_NOT_FOUND", "地区目录中没有匹配项");
        }
        return toMatch(matches.get(0), "unique_name_match");
    }

    public RegionScope resolveScope(Long selectedRegionId, String requestedScope) {
        if (selectedRegionId == null || selectedRegionId <= 0) {
            throw new AgentToolException("INVALID_REGION_ID", "研究画像中的地区编号无效");
        }
        Region selected = regionMapper.selectById(selectedRegionId);
        if (selected == null) {
            throw new AgentToolException("INVALID_REGION_ID", "研究画像中的地区不存在");
        }
        String scope = StringUtils.hasText(requestedScope) ? requestedScope.trim() : "selected";
        if ("selected".equals(scope) || "cross_region_reference".equals(scope)) {
            return new RegionScope(scope, List.of(selected.getId()));
        }
        if (!Set.of("parent", "national").contains(scope)) {
            throw new AgentToolException("INVALID_REGION_SCOPE", "地区检索范围无效");
        }

        if ("country".equals(selected.getLevel())) {
            return new RegionScope("national", List.of(selected.getId()));
        }

        List<Long> parentIds = new ArrayList<>();
        List<Long> nationalIds = new ArrayList<>();
        Set<Long> visited = new LinkedHashSet<>();
        Long currentId = selected.getParentId();
        int depth = 0;
        while (currentId != null && visited.add(currentId) && depth++ < 16) {
            Region current = regionMapper.selectById(currentId);
            if (current == null) break;
            if ("country".equals(current.getLevel())) nationalIds.add(current.getId());
            else parentIds.add(current.getId());
            currentId = current.getParentId();
        }
        if ("parent".equals(scope) && parentIds.isEmpty() && !nationalIds.isEmpty()) {
            return new RegionScope("national", List.copyOf(nationalIds));
        }
        List<Long> resolved = "parent".equals(scope) ? parentIds : nationalIds;
        if (resolved.isEmpty()) {
            throw new AgentToolException("REGION_SCOPE_EMPTY", "所选地区没有可用的上级检索范围");
        }
        return new RegionScope(scope, List.copyOf(resolved));
    }

    private List<Region> safe(List<Region> matches) {
        return matches == null ? List.of() : matches.stream().filter(java.util.Objects::nonNull).toList();
    }

    private AgentRegionMatch toMatch(Region region, String reason) {
        return new AgentRegionMatch(
                region.getId(), region.getName(), region.getLevel(), region.getParentId(), reason
        );
    }

    public record RegionScope(String scope, List<Long> regionIds) {
    }
}
