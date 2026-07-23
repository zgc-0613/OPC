package com.opc.platform.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.dashboard.vo.DashboardSummaryVO;
import com.opc.platform.dashboard.vo.RecentUpdateVO;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int RECENT_LIMIT = 10;

    private static final String PUBLISHED_STATUS = "published";

    private final PolicyMapper policyMapper;

    private final CaseItemMapper caseItemMapper;

    private final SourceMapper sourceMapper;

    private final RegionMapper regionMapper;

    public DashboardSummaryVO getSummary() {
        DashboardSummaryVO summary = new DashboardSummaryVO();
        summary.setPolicyCount(policyMapper.selectCount(new LambdaQueryWrapper<Policy>()
                .eq(Policy::getStatus, PUBLISHED_STATUS)));
        summary.setCaseCount(caseItemMapper.selectCount(new LambdaQueryWrapper<CaseItem>()
                .eq(CaseItem::getStatus, PUBLISHED_STATUS)));
        summary.setSourceCount(sourceMapper.selectCount(new LambdaQueryWrapper<Source>()
                .eq(Source::getStatus, PUBLISHED_STATUS)));
        summary.setCoveredRegionCount(countCoveredRegions());
        summary.setRecentUpdates(listRecentUpdates());
        return summary;
    }

    private Integer countCoveredRegions() {
        Set<Long> regionIds = new HashSet<>();
        policyMapper.selectObjs(new QueryWrapper<Policy>()
                        .select("DISTINCT region_id")
                        .eq("status", PUBLISHED_STATUS))
                .forEach(regionId -> regionIds.add(((Number) regionId).longValue()));
        caseItemMapper.selectObjs(new QueryWrapper<CaseItem>()
                        .select("DISTINCT region_id")
                        .eq("status", PUBLISHED_STATUS))
                .forEach(regionId -> regionIds.add(((Number) regionId).longValue()));
        return regionIds.size();
    }

    private List<RecentUpdateVO> listRecentUpdates() {
        List<Policy> policies = policyMapper.selectList(new LambdaQueryWrapper<Policy>()
                .eq(Policy::getStatus, PUBLISHED_STATUS)
                .orderByDesc(Policy::getAccessedAt)
                .orderByDesc(Policy::getId)
                .last("LIMIT " + RECENT_LIMIT));
        List<CaseItem> caseItems = caseItemMapper.selectList(new LambdaQueryWrapper<CaseItem>()
                .eq(CaseItem::getStatus, PUBLISHED_STATUS)
                .orderByDesc(CaseItem::getAccessedAt)
                .orderByDesc(CaseItem::getId)
                .last("LIMIT " + RECENT_LIMIT));
        List<Source> sources = sourceMapper.selectList(new LambdaQueryWrapper<Source>()
                .eq(Source::getStatus, PUBLISHED_STATUS)
                .orderByDesc(Source::getAccessedAt)
                .orderByDesc(Source::getId)
                .last("LIMIT " + RECENT_LIMIT));

        Map<Long, Region> regionMap = loadRegionMap(policies, caseItems);
        List<RecentUpdateVO> updates = new ArrayList<>();
        policies.forEach(policy -> updates.add(toRecentUpdate(policy, regionMap)));
        caseItems.forEach(caseItem -> updates.add(toRecentUpdate(caseItem, regionMap)));
        sources.forEach(source -> updates.add(toRecentUpdate(source)));

        return updates.stream()
                .sorted(Comparator.comparing(RecentUpdateVO::getUpdatedDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_LIMIT)
                .toList();
    }

    private Map<Long, Region> loadRegionMap(List<Policy> policies, List<CaseItem> caseItems) {
        Set<Long> regionIds = new HashSet<>();
        policies.forEach(policy -> regionIds.add(policy.getRegionId()));
        caseItems.forEach(caseItem -> regionIds.add(caseItem.getRegionId()));
        if (regionIds.isEmpty()) {
            return Map.of();
        }
        return regionMapper.selectBatchIds(regionIds).stream()
                .collect(Collectors.toMap(Region::getId, Function.identity()));
    }

    private RecentUpdateVO toRecentUpdate(Policy policy, Map<Long, Region> regionMap) {
        RecentUpdateVO vo = new RecentUpdateVO();
        vo.setItemType("policy");
        vo.setItemId(policy.getId());
        vo.setTitle(policy.getTitle());
        vo.setRegionId(policy.getRegionId());
        Region region = regionMap.get(policy.getRegionId());
        vo.setRegionName(region == null ? null : region.getName());
        vo.setUpdatedDate(policy.getAccessedAt());
        vo.setStatus(policy.getStatus());
        return vo;
    }

    private RecentUpdateVO toRecentUpdate(CaseItem caseItem, Map<Long, Region> regionMap) {
        RecentUpdateVO vo = new RecentUpdateVO();
        vo.setItemType("case");
        vo.setItemId(caseItem.getId());
        vo.setTitle(caseItem.getTitle());
        vo.setRegionId(caseItem.getRegionId());
        Region region = regionMap.get(caseItem.getRegionId());
        vo.setRegionName(region == null ? null : region.getName());
        vo.setUpdatedDate(caseItem.getAccessedAt());
        vo.setStatus(caseItem.getStatus());
        return vo;
    }

    private RecentUpdateVO toRecentUpdate(Source source) {
        RecentUpdateVO vo = new RecentUpdateVO();
        vo.setItemType("source");
        vo.setItemId(source.getId());
        vo.setTitle(source.getTitle());
        vo.setUpdatedDate(source.getAccessedAt());
        vo.setStatus(source.getStatus());
        return vo;
    }
}
