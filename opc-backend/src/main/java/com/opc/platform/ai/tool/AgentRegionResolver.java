package com.opc.platform.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

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

    private List<Region> safe(List<Region> matches) {
        return matches == null ? List.of() : matches.stream().filter(java.util.Objects::nonNull).toList();
    }

    private AgentRegionMatch toMatch(Region region, String reason) {
        return new AgentRegionMatch(
                region.getId(), region.getName(), region.getLevel(), region.getParentId(), reason
        );
    }
}
