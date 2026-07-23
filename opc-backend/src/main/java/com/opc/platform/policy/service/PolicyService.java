package com.opc.platform.policy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.dto.PolicyCreateDTO;
import com.opc.platform.policy.dto.PolicyQueryDTO;
import com.opc.platform.policy.dto.PolicyUpdateDTO;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policy.vo.PolicyDetailVO;
import com.opc.platform.policy.vo.PolicyListVO;
import com.opc.platform.policytag.entity.PolicyTag;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private static final String POLICY_TAG_TYPE = "policy";

    private static final String PUBLISHED_STATUS = "published";

    private final PolicyMapper policyMapper;

    private final RegionMapper regionMapper;

    private final SourceMapper sourceMapper;

    private final TagMapper tagMapper;

    private final PolicyTagMapper policyTagMapper;

    @Transactional
    public PolicyDetailVO createPolicy(PolicyCreateDTO dto) {
        validateRegionAndSource(dto.getRegionId(), dto.getSourceId());

        Policy policy = new Policy();
        copyCreateFields(dto, policy);
        policyMapper.insert(policy);
        syncPolicyTags(policy.getId(), policy.getTags());
        return getPolicyDetail(policy.getId());
    }

    @Transactional
    public PolicyDetailVO updatePolicy(Long id, PolicyUpdateDTO dto) {
        Policy policy = policyMapper.selectById(id);
        if (policy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }
        validateRegionAndSource(dto.getRegionId(), dto.getSourceId());

        copyUpdateFields(dto, policy);
        policyMapper.updateById(policy);
        syncPolicyTags(id, policy.getTags());
        return getPolicyDetail(id);
    }

    @Transactional
    public void deletePolicy(Long id) {
        Policy policy = policyMapper.selectById(id);
        if (policy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }
        policyTagMapper.delete(new LambdaQueryWrapper<PolicyTag>()
                .eq(PolicyTag::getPolicyId, id));
        policyMapper.deleteById(id);
    }

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

    public List<PolicyListVO> listPublicPolicies(PolicyQueryDTO query) {
        PolicyQueryDTO publicQuery = new PolicyQueryDTO();
        if (query != null) {
            publicQuery.setKeyword(query.getKeyword());
            publicQuery.setRegionId(query.getRegionId());
            publicQuery.setPolicyType(query.getPolicyType());
        }
        publicQuery.setStatus(PUBLISHED_STATUS);
        return listPolicies(publicQuery);
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

    public PolicyDetailVO getPublicPolicyDetail(Long id) {
        Policy policy = policyMapper.selectById(id);
        if (policy == null || !PUBLISHED_STATUS.equals(policy.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }

        Region region = regionMapper.selectById(policy.getRegionId());
        Source source = sourceMapper.selectById(policy.getSourceId());
        return toDetailVO(policy, region, source);
    }

    private void validateRegionAndSource(Long regionId, Long sourceId) {
        if (regionMapper.selectById(regionId) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Region not found");
        }
        if (sourceMapper.selectById(sourceId) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Source not found");
        }
    }

    private void copyCreateFields(PolicyCreateDTO dto, Policy policy) {
        policy.setTitle(dto.getTitle());
        policy.setRegionId(dto.getRegionId());
        policy.setIssuingBody(dto.getIssuingBody());
        policy.setDocumentNo(dto.getDocumentNo());
        policy.setPublishDate(dto.getPublishDate());
        policy.setEffectiveDate(dto.getEffectiveDate());
        policy.setValidPeriod(dto.getValidPeriod());
        policy.setSourceId(dto.getSourceId());
        policy.setPolicyLevel(dto.getPolicyLevel());
        policy.setPolicyType(dto.getPolicyType());
        policy.setSummary(dto.getSummary());
        policy.setKeyPoints(dto.getKeyPoints());
        policy.setSupportMeasures(dto.getSupportMeasures());
        policy.setTags(normalizeTags(dto.getTags()));
        policy.setOriginalUrl(dto.getOriginalUrl());
        policy.setEvidenceUrl(dto.getEvidenceUrl());
        policy.setLocalFile(dto.getLocalFile());
        policy.setAccessedAt(dto.getAccessedAt());
        policy.setStatus(dto.getStatus());
        policy.setReviewer(dto.getReviewer());
        policy.setAiEvidenceStatus(normalizeEvidenceStatus(dto.getAiEvidenceStatus(), "legacy_unverified"));
    }

    private void copyUpdateFields(PolicyUpdateDTO dto, Policy policy) {
        policy.setTitle(dto.getTitle());
        policy.setRegionId(dto.getRegionId());
        policy.setIssuingBody(dto.getIssuingBody());
        policy.setDocumentNo(dto.getDocumentNo());
        policy.setPublishDate(dto.getPublishDate());
        policy.setEffectiveDate(dto.getEffectiveDate());
        policy.setValidPeriod(dto.getValidPeriod());
        policy.setSourceId(dto.getSourceId());
        policy.setPolicyLevel(dto.getPolicyLevel());
        policy.setPolicyType(dto.getPolicyType());
        policy.setSummary(dto.getSummary());
        policy.setKeyPoints(dto.getKeyPoints());
        policy.setSupportMeasures(dto.getSupportMeasures());
        policy.setTags(normalizeTags(dto.getTags()));
        policy.setOriginalUrl(dto.getOriginalUrl());
        policy.setEvidenceUrl(dto.getEvidenceUrl());
        policy.setLocalFile(dto.getLocalFile());
        policy.setAccessedAt(dto.getAccessedAt());
        policy.setStatus(dto.getStatus());
        policy.setReviewer(dto.getReviewer());
        policy.setAiEvidenceStatus(normalizeEvidenceStatus(dto.getAiEvidenceStatus(), policy.getAiEvidenceStatus()));
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
        vo.setDocumentNo(policy.getDocumentNo());
        vo.setPublishDate(policy.getPublishDate());
        vo.setEffectiveDate(policy.getEffectiveDate());
        vo.setValidPeriod(policy.getValidPeriod());
        vo.setSourceId(policy.getSourceId());
        vo.setSourceTitle(source == null ? null : source.getTitle());
        vo.setPolicyLevel(policy.getPolicyLevel());
        vo.setPolicyType(policy.getPolicyType());
        vo.setSummary(policy.getSummary());
        vo.setTags(policy.getTags());
        vo.setEvidenceUrl(policy.getEvidenceUrl());
        vo.setAccessedAt(policy.getAccessedAt());
        vo.setStatus(policy.getStatus());
        vo.setAiEvidenceStatus(policy.getAiEvidenceStatus());
        return vo;
    }

    private PolicyDetailVO toDetailVO(Policy policy, Region region, Source source) {
        PolicyDetailVO vo = new PolicyDetailVO();
        vo.setId(policy.getId());
        vo.setTitle(policy.getTitle());
        vo.setRegionId(policy.getRegionId());
        vo.setRegionName(region == null ? null : region.getName());
        vo.setIssuingBody(policy.getIssuingBody());
        vo.setDocumentNo(policy.getDocumentNo());
        vo.setPublishDate(policy.getPublishDate());
        vo.setEffectiveDate(policy.getEffectiveDate());
        vo.setValidPeriod(policy.getValidPeriod());
        vo.setSourceId(policy.getSourceId());
        vo.setSourceTitle(source == null ? null : source.getTitle());
        vo.setPolicyLevel(policy.getPolicyLevel());
        vo.setPolicyType(policy.getPolicyType());
        vo.setSummary(policy.getSummary());
        vo.setKeyPoints(policy.getKeyPoints());
        vo.setSupportMeasures(policy.getSupportMeasures());
        vo.setTags(policy.getTags());
        vo.setOriginalUrl(policy.getOriginalUrl());
        vo.setEvidenceUrl(policy.getEvidenceUrl());
        vo.setLocalFile(policy.getLocalFile());
        vo.setAccessedAt(policy.getAccessedAt());
        vo.setStatus(policy.getStatus());
        vo.setReviewer(policy.getReviewer());
        vo.setAiEvidenceStatus(policy.getAiEvidenceStatus());
        return vo;
    }

    private String normalizeEvidenceStatus(String requested, String fallback) {
        return StringUtils.hasText(requested) ? requested.trim() : fallback;
    }

    private void syncPolicyTags(Long policyId, String tagsText) {
        policyTagMapper.delete(new LambdaQueryWrapper<PolicyTag>()
                .eq(PolicyTag::getPolicyId, policyId));

        Set<String> tagNames = parseTagNames(tagsText);
        for (String tagName : tagNames) {
            Tag tag = getOrCreatePolicyTag(tagName);
            PolicyTag policyTag = new PolicyTag();
            policyTag.setPolicyId(policyId);
            policyTag.setTagId(tag.getId());
            policyTagMapper.insert(policyTag);
        }
    }

    private Tag getOrCreatePolicyTag(String tagName) {
        Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, tagName)
                .eq(Tag::getTagType, POLICY_TAG_TYPE));
        if (tag != null) {
            return tag;
        }

        Tag newTag = new Tag();
        newTag.setName(tagName);
        newTag.setTagType(POLICY_TAG_TYPE);
        newTag.setSortOrder(0);
        tagMapper.insert(newTag);
        return tagMapper.selectById(newTag.getId());
    }

    private String normalizeTags(String tagsText) {
        Set<String> tagNames = parseTagNames(tagsText);
        if (tagNames.isEmpty()) {
            return null;
        }
        return String.join(",", tagNames);
    }

    private Set<String> parseTagNames(String tagsText) {
        if (!StringUtils.hasText(tagsText)) {
            return Collections.emptySet();
        }
        return Arrays.stream(tagsText.split("[,，]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
