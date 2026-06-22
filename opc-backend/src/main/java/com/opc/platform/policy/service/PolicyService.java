package com.opc.platform.policy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.dto.PolicyQueryDTO;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policy.vo.PolicyDetailVO;
import com.opc.platform.policy.vo.PolicyListVO;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyMapper policyMapper;

    private final RegionMapper regionMapper;

    private final SourceMapper sourceMapper;

    public List<PolicyListVO> listPolicies(PolicyQueryDTO query) {
        List<Policy> policies = policyMapper.selectList(buildQueryWrapper(query)
                .orderByDesc(Policy::getPublishDate)
                .orderByDesc(Policy::getId));

        Map<Long, Region> regionMap = loadRegionMap(policies);
        Map<Long, Source> sourceMap = loadSourceMap(policies);

        return policies.stream()
                .map(policy -> toListVO(policy, regionMap, sourceMap))
                .toList();
    }

    public PolicyDetailVO getPolicyDetail(Long id) {
        Policy policy = policyMapper.selectById(id);
        if (policy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }

        Region region = regionMapper.selectById(policy.getRegionId());
        Source source = sourceMapper.selectById(policy.getSourceId());
        return toDetailVO(policy, region, source);
    }

    private LambdaQueryWrapper<Policy> buildQueryWrapper(PolicyQueryDTO query) {
        LambdaQueryWrapper<Policy> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper;
        }

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(item -> item
                    .like(Policy::getTitle, query.getKeyword())
                    .or()
                    .like(Policy::getSummary, query.getKeyword())
                    .or()
                    .like(Policy::getTags, query.getKeyword()));
        }
        if (query.getRegionId() != null) {
            wrapper.eq(Policy::getRegionId, query.getRegionId());
        }
        if (StringUtils.hasText(query.getPolicyType())) {
            wrapper.eq(Policy::getPolicyType, query.getPolicyType());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Policy::getStatus, query.getStatus());
        }
        return wrapper;
    }

    private Map<Long, Region> loadRegionMap(List<Policy> policies) {
        List<Long> regionIds = policies.stream()
                .map(Policy::getRegionId)
                .distinct()
                .toList();
        if (regionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return regionMapper.selectBatchIds(regionIds).stream()
                .collect(Collectors.toMap(Region::getId, Function.identity()));
    }

    private Map<Long, Source> loadSourceMap(List<Policy> policies) {
        List<Long> sourceIds = policies.stream()
                .map(Policy::getSourceId)
                .distinct()
                .toList();
        if (sourceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sourceMapper.selectBatchIds(sourceIds).stream()
                .collect(Collectors.toMap(Source::getId, Function.identity()));
    }

    private PolicyListVO toListVO(Policy policy, Map<Long, Region> regionMap, Map<Long, Source> sourceMap) {
        Region region = regionMap.get(policy.getRegionId());
        Source source = sourceMap.get(policy.getSourceId());

        PolicyListVO vo = new PolicyListVO();
        vo.setId(policy.getId());
        vo.setTitle(policy.getTitle());
        vo.setRegionId(policy.getRegionId());
        vo.setRegionName(region == null ? null : region.getName());
        vo.setIssuingBody(policy.getIssuingBody());
        vo.setPublishDate(policy.getPublishDate());
        vo.setSourceId(policy.getSourceId());
        vo.setSourceTitle(source == null ? null : source.getTitle());
        vo.setPolicyLevel(policy.getPolicyLevel());
        vo.setPolicyType(policy.getPolicyType());
        vo.setSummary(policy.getSummary());
        vo.setTags(policy.getTags());
        vo.setAccessedAt(policy.getAccessedAt());
        vo.setStatus(policy.getStatus());
        return vo;
    }

    private PolicyDetailVO toDetailVO(Policy policy, Region region, Source source) {
        PolicyDetailVO vo = new PolicyDetailVO();
        vo.setId(policy.getId());
        vo.setTitle(policy.getTitle());
        vo.setRegionId(policy.getRegionId());
        vo.setRegionName(region == null ? null : region.getName());
        vo.setIssuingBody(policy.getIssuingBody());
        vo.setPublishDate(policy.getPublishDate());
        vo.setSourceId(policy.getSourceId());
        vo.setSourceTitle(source == null ? null : source.getTitle());
        vo.setPolicyLevel(policy.getPolicyLevel());
        vo.setPolicyType(policy.getPolicyType());
        vo.setSummary(policy.getSummary());
        vo.setKeyPoints(policy.getKeyPoints());
        vo.setSupportMeasures(policy.getSupportMeasures());
        vo.setTags(policy.getTags());
        vo.setOriginalUrl(policy.getOriginalUrl());
        vo.setLocalFile(policy.getLocalFile());
        vo.setAccessedAt(policy.getAccessedAt());
        vo.setStatus(policy.getStatus());
        vo.setReviewer(policy.getReviewer());
        return vo;
    }
}
