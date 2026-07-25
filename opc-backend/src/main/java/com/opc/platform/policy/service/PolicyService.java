package com.opc.platform.policy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.service.EvidenceReviewService;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.dto.PolicyCreateDTO;
import com.opc.platform.policy.dto.PolicyApplicabilityBatchDTO;
import com.opc.platform.policy.dto.PolicyApplicabilityBatchItemDTO;
import com.opc.platform.policy.dto.PolicyQueryDTO;
import com.opc.platform.policy.dto.PolicyUpdateDTO;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policyindustrytag.entity.PolicyIndustryTag;
import com.opc.platform.policyindustrytag.mapper.PolicyIndustryTagMapper;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
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

    private final PolicyIndustryTagMapper policyIndustryTagMapper;

    private final EvidenceReviewService evidenceReviewService;

    @Transactional
    public PolicyDetailVO createPolicy(PolicyCreateDTO dto) {
        validateRegionAndSource(dto.getRegionId(), dto.getSourceId());
        Applicability applicability = validateApplicability(dto.getApplicabilityMode(), dto.getIndustryTagIds(), true);

        Policy policy = new Policy();
        copyCreateFields(dto, policy, applicability);
        policyMapper.insert(policy);
        syncPolicyTags(policy.getId(), policy.getTags());
        syncPolicyIndustryTags(policy.getId(), applicability.industryTagIds());
        return getPolicyDetail(policy.getId());
    }

    @Transactional
    public PolicyDetailVO updatePolicy(Long id, PolicyUpdateDTO dto, AuthenticatedAdmin admin) {
        Policy snapshot = policyMapper.selectById(id);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }
        requireEditSnapshot(snapshot.getEvidenceRevision(), snapshot.getUpdatedAt(),
                dto.getExpectedEvidenceRevision(), dto.getExpectedUpdatedAt());
        validateRegion(dto.getRegionId());
        lockSources(snapshot.getSourceId(), dto.getSourceId());

        Policy policy = policyMapper.selectByIdForUpdate(id);
        if (policy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }
        requireEditSnapshot(policy.getEvidenceRevision(), policy.getUpdatedAt(),
                dto.getExpectedEvidenceRevision(), dto.getExpectedUpdatedAt());

        Set<Long> currentIndustryTagIds = policyIndustryTagIds(id);
        Applicability applicability = resolveUpdateApplicability(policy, currentIndustryTagIds, dto);
        boolean evidenceChanged = evidenceRelevantFieldsChanged(policy, currentIndustryTagIds, dto, applicability);
        copyUpdateFields(dto, policy, applicability);
        if (evidenceChanged) {
            evidenceReviewService.invalidatePolicyAfterEvidenceEdit(policy, admin);
        }
        policy.setUpdatedAt(null);
        requireSingleWrite(policyMapper.updateById(policy));
        syncPolicyTags(id, policy.getTags());
        syncPolicyIndustryTags(id, applicability.industryTagIds());
        return getPolicyDetail(id);
    }

    @Transactional
    public void deletePolicy(Long id, Long expectedEvidenceRevision, java.time.LocalDateTime expectedUpdatedAt) {
        Policy snapshot = policyMapper.selectById(id);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }
        requireEditSnapshot(snapshot.getEvidenceRevision(), snapshot.getUpdatedAt(),
                expectedEvidenceRevision, expectedUpdatedAt);
        lockSources(snapshot.getSourceId());
        Policy policy = policyMapper.selectByIdForUpdate(id);
        if (policy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }
        requireEditSnapshot(policy.getEvidenceRevision(), policy.getUpdatedAt(),
                expectedEvidenceRevision, expectedUpdatedAt);
        evidenceReviewService.requireReviewedItemDeletionAllowed("policy", policy.getAiEvidenceStatus());
        policyTagMapper.delete(new LambdaQueryWrapper<PolicyTag>()
                .eq(PolicyTag::getPolicyId, id));
        policyIndustryTagMapper.delete(new LambdaQueryWrapper<PolicyIndustryTag>()
                .eq(PolicyIndustryTag::getPolicyId, id));
        requireSingleWrite(policyMapper.deleteById(id));
    }

    @Transactional
    public void updateApplicabilityBatch(
            PolicyApplicabilityBatchDTO dto,
            AuthenticatedAdmin admin
    ) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty() || dto.getItems().size() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "政策批量分类每次最多处理 100 条记录");
        }
        Applicability applicability = validateApplicability(
                dto.getApplicabilityMode(), dto.getIndustryTagIds(), false
        );
        List<PolicyApplicabilityBatchItemDTO> items = dto.getItems().stream()
                .sorted(java.util.Comparator.comparing(PolicyApplicabilityBatchItemDTO::getPolicyId))
                .toList();
        if (items.stream().map(PolicyApplicabilityBatchItemDTO::getPolicyId).distinct().count() != items.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "批量政策中存在重复记录");
        }

        List<Policy> snapshots = new ArrayList<>();
        for (PolicyApplicabilityBatchItemDTO item : items) {
            Policy snapshot = policyMapper.selectById(item.getPolicyId());
            if (snapshot == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
            }
            requireEditSnapshot(snapshot.getEvidenceRevision(), snapshot.getUpdatedAt(),
                    item.getExpectedEvidenceRevision(), item.getExpectedUpdatedAt());
            snapshots.add(snapshot);
        }
        lockSources(snapshots.stream().map(Policy::getSourceId).distinct().toArray(Long[]::new));

        Map<Long, PolicyApplicabilityBatchItemDTO> snapshotsById = items.stream().collect(Collectors.toMap(
                PolicyApplicabilityBatchItemDTO::getPolicyId, Function.identity()
        ));
        for (PolicyApplicabilityBatchItemDTO item : items) {
            Policy policy = policyMapper.selectByIdForUpdate(item.getPolicyId());
            if (policy == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
            }
            PolicyApplicabilityBatchItemDTO expected = snapshotsById.get(policy.getId());
            requireEditSnapshot(policy.getEvidenceRevision(), policy.getUpdatedAt(),
                    expected.getExpectedEvidenceRevision(), expected.getExpectedUpdatedAt());
            Set<Long> currentIndustryTagIds = policyIndustryTagIds(policy.getId());
            if (Objects.equals(safeApplicabilityMode(policy.getApplicabilityMode()), applicability.mode())
                    && Objects.equals(currentIndustryTagIds, applicability.industryTagIds())) {
                continue;
            }
            policy.setApplicabilityMode(applicability.mode());
            evidenceReviewService.invalidatePolicyAfterEvidenceEdit(policy, admin);
            policy.setUpdatedAt(null);
            requireSingleWrite(policyMapper.updateById(policy));
            syncPolicyIndustryTags(policy.getId(), applicability.industryTagIds());
        }
    }

    public List<PolicyListVO> listPolicies(PolicyQueryDTO query) {
        List<Policy> policies = policyMapper.selectList(buildQueryWrapper(query)
                .orderByDesc(Policy::getPublishDate)
                .orderByDesc(Policy::getId));

        Map<Long, Region> regionMap = loadRegionMap(policies);
        Map<Long, Source> sourceMap = loadSourceMap(policies);
        Map<Long, Set<Long>> industryRelations = loadPolicyIndustryRelations(
                policies.stream().map(Policy::getId).toList()
        );
        Map<Long, String> industryNames = loadIndustryNames(industryRelations.values().stream()
                .flatMap(Set::stream).collect(Collectors.toSet()));

        return policies.stream()
                .map(policy -> toListVO(policy, regionMap, sourceMap, industryRelations, industryNames))
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
        Set<Long> industryTagIds = policyIndustryTagIds(id);
        return toDetailVO(policy, region, source, industryTagIds, loadIndustryNames(industryTagIds));
    }

    public PolicyDetailVO getPublicPolicyDetail(Long id) {
        Policy policy = policyMapper.selectById(id);
        if (policy == null || !PUBLISHED_STATUS.equals(policy.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }

        Region region = regionMapper.selectById(policy.getRegionId());
        Source source = sourceMapper.selectById(policy.getSourceId());
        Set<Long> industryTagIds = policyIndustryTagIds(id);
        return toDetailVO(policy, region, source, industryTagIds, loadIndustryNames(industryTagIds));
    }

    private void validateRegionAndSource(Long regionId, Long sourceId) {
        validateRegion(regionId);
        lockSources(sourceId);
    }

    private void validateRegion(Long regionId) {
        if (regionMapper.selectById(regionId) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Region not found");
        }
    }

    private void lockSources(Long... sourceIds) {
        TreeSet<Long> orderedIds = Arrays.stream(sourceIds)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        for (Long sourceId : orderedIds) {
            if (sourceMapper.selectByIdForUpdate(sourceId) == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Source not found");
            }
        }
    }

    private void requireEditSnapshot(
            Long actualRevision,
            java.time.LocalDateTime actualUpdatedAt,
            Long expectedRevision,
            java.time.LocalDateTime expectedUpdatedAt
    ) {
        if (expectedRevision == null || expectedUpdatedAt == null
                || !Objects.equals(actualRevision == null ? 0L : actualRevision, expectedRevision)
                || !Objects.equals(actualUpdatedAt, expectedUpdatedAt)) {
            throw new BusinessException(ErrorCode.CONFLICT, "政策已被其他操作修改，请重新加载后再保存");
        }
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "政策已被其他操作修改，请重新加载后重试");
        }
    }

    private Applicability resolveUpdateApplicability(
            Policy policy,
            Set<Long> currentIndustryTagIds,
            PolicyUpdateDTO dto
    ) {
        if (!StringUtils.hasText(dto.getApplicabilityMode()) && dto.getIndustryTagIds() == null) {
            return new Applicability(safeApplicabilityMode(policy.getApplicabilityMode()), Set.copyOf(currentIndustryTagIds));
        }
        if (!StringUtils.hasText(dto.getApplicabilityMode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "设置适用行业时必须选择政策适用范围");
        }
        String requestedMode = dto.getApplicabilityMode().trim().toLowerCase(java.util.Locale.ROOT);
        if (dto.getIndustryTagIds() == null
                && Objects.equals(safeApplicabilityMode(policy.getApplicabilityMode()), requestedMode)) {
            return new Applicability(requestedMode, Set.copyOf(currentIndustryTagIds));
        }
        return validateApplicability(dto.getApplicabilityMode(), dto.getIndustryTagIds(), false);
    }

    private Applicability validateApplicability(
            String rawMode,
            List<Long> rawIndustryTagIds,
            boolean defaultUnclassified
    ) {
        String mode = StringUtils.hasText(rawMode)
                ? rawMode.trim().toLowerCase(java.util.Locale.ROOT)
                : defaultUnclassified ? "unclassified" : null;
        if (mode == null || !Set.of("general", "specific", "unclassified").contains(mode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "政策适用范围必须是通用、指定行业或未分类");
        }
        Set<Long> industryTagIds = rawIndustryTagIds == null
                ? Set.of()
                : rawIndustryTagIds.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(TreeSet::new));
        if ("specific".equals(mode) && industryTagIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "指定行业政策必须至少选择一个行业标签");
        }
        if (!"specific".equals(mode) && !industryTagIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "通用或未分类政策不能关联指定行业");
        }
        if (!industryTagIds.isEmpty()) {
            List<Tag> tags = tagMapper.selectBatchIds(industryTagIds);
            if (tags == null || tags.size() != industryTagIds.size()
                    || tags.stream().anyMatch(tag -> !Boolean.TRUE.equals(tag.getIsIndustry()))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "所选政策适用行业包含无效标签");
            }
        }
        return new Applicability(mode, Set.copyOf(industryTagIds));
    }

    private String safeApplicabilityMode(String value) {
        return value != null && Set.of("general", "specific", "unclassified").contains(value)
                ? value
                : "unclassified";
    }

    private void copyCreateFields(PolicyCreateDTO dto, Policy policy, Applicability applicability) {
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
        policy.setApplicabilityMode(applicability.mode());
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
        policy.setAiEvidenceStatus("legacy_unverified");
        policy.setEvidenceRevision(0L);
    }

    private void copyUpdateFields(PolicyUpdateDTO dto, Policy policy, Applicability applicability) {
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
        policy.setApplicabilityMode(applicability.mode());
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
    }

    private boolean evidenceRelevantFieldsChanged(
            Policy current,
            Set<Long> currentIndustryTagIds,
            PolicyUpdateDTO dto,
            Applicability applicability
    ) {
        return !Objects.equals(current.getTitle(), dto.getTitle())
                || !Objects.equals(current.getRegionId(), dto.getRegionId())
                || !Objects.equals(current.getIssuingBody(), dto.getIssuingBody())
                || !Objects.equals(current.getDocumentNo(), dto.getDocumentNo())
                || !Objects.equals(current.getPublishDate(), dto.getPublishDate())
                || !Objects.equals(current.getEffectiveDate(), dto.getEffectiveDate())
                || !Objects.equals(current.getValidPeriod(), dto.getValidPeriod())
                || !Objects.equals(current.getSourceId(), dto.getSourceId())
                || !Objects.equals(current.getPolicyLevel(), dto.getPolicyLevel())
                || !Objects.equals(current.getPolicyType(), dto.getPolicyType())
                || !Objects.equals(safeApplicabilityMode(current.getApplicabilityMode()), applicability.mode())
                || !Objects.equals(currentIndustryTagIds, applicability.industryTagIds())
                || !Objects.equals(current.getSummary(), dto.getSummary())
                || !Objects.equals(current.getKeyPoints(), dto.getKeyPoints())
                || !Objects.equals(current.getSupportMeasures(), dto.getSupportMeasures())
                || !Objects.equals(current.getTags(), normalizeTags(dto.getTags()))
                || !Objects.equals(current.getOriginalUrl(), dto.getOriginalUrl())
                || !Objects.equals(current.getEvidenceUrl(), dto.getEvidenceUrl())
                || !Objects.equals(current.getLocalFile(), dto.getLocalFile())
                || !Objects.equals(current.getAccessedAt(), dto.getAccessedAt())
                || !Objects.equals(current.getStatus(), dto.getStatus());
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

    private PolicyListVO toListVO(
            Policy policy,
            Map<Long, Region> regionMap,
            Map<Long, Source> sourceMap,
            Map<Long, Set<Long>> industryRelations,
            Map<Long, String> industryNames
    ) {
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
        vo.setApplicabilityMode(safeApplicabilityMode(policy.getApplicabilityMode()));
        List<Long> industryTagIds = industryRelations.getOrDefault(policy.getId(), Set.of()).stream().sorted().toList();
        vo.setIndustryTagIds(industryTagIds);
        vo.setIndustryTagNames(industryTagIds.stream().map(industryNames::get).filter(Objects::nonNull).toList());
        vo.setSummary(policy.getSummary());
        vo.setTags(policy.getTags());
        vo.setEvidenceUrl(policy.getEvidenceUrl());
        vo.setAccessedAt(policy.getAccessedAt());
        vo.setStatus(policy.getStatus());
        vo.setAiEvidenceStatus(policy.getAiEvidenceStatus());
        vo.setEvidenceRevision(policy.getEvidenceRevision());
        vo.setUpdatedAt(policy.getUpdatedAt());
        return vo;
    }

    private PolicyDetailVO toDetailVO(
            Policy policy,
            Region region,
            Source source,
            Set<Long> industryTagIds,
            Map<Long, String> industryNames
    ) {
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
        vo.setApplicabilityMode(safeApplicabilityMode(policy.getApplicabilityMode()));
        List<Long> orderedIndustryTagIds = industryTagIds.stream().sorted().toList();
        vo.setIndustryTagIds(orderedIndustryTagIds);
        vo.setIndustryTagNames(orderedIndustryTagIds.stream().map(industryNames::get).filter(Objects::nonNull).toList());
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
        vo.setEvidenceRevision(policy.getEvidenceRevision());
        vo.setUpdatedAt(policy.getUpdatedAt());
        return vo;
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

    private void syncPolicyIndustryTags(Long policyId, Collection<Long> industryTagIds) {
        policyIndustryTagMapper.delete(new LambdaQueryWrapper<PolicyIndustryTag>()
                .eq(PolicyIndustryTag::getPolicyId, policyId));
        for (Long industryTagId : new TreeSet<>(industryTagIds)) {
            PolicyIndustryTag relation = new PolicyIndustryTag();
            relation.setPolicyId(policyId);
            relation.setIndustryTagId(industryTagId);
            policyIndustryTagMapper.insert(relation);
        }
    }

    private Set<Long> policyIndustryTagIds(Long policyId) {
        return loadPolicyIndustryRelations(List.of(policyId)).getOrDefault(policyId, Set.of());
    }

    private Map<Long, Set<Long>> loadPolicyIndustryRelations(Collection<Long> policyIds) {
        if (policyIds == null || policyIds.isEmpty()) {
            return Map.of();
        }
        List<PolicyIndustryTag> relations = policyIndustryTagMapper.selectList(
                new LambdaQueryWrapper<PolicyIndustryTag>().in(PolicyIndustryTag::getPolicyId, policyIds)
        );
        if (relations == null) {
            return Map.of();
        }
        return relations.stream().collect(Collectors.groupingBy(
                PolicyIndustryTag::getPolicyId,
                Collectors.mapping(PolicyIndustryTag::getIndustryTagId, Collectors.toCollection(TreeSet::new))
        ));
    }

    private Map<Long, String> loadIndustryNames(Collection<Long> industryTagIds) {
        if (industryTagIds == null || industryTagIds.isEmpty()) {
            return Map.of();
        }
        List<Tag> tags = tagMapper.selectBatchIds(industryTagIds);
        if (tags == null) {
            return Map.of();
        }
        return tags.stream().filter(tag -> Boolean.TRUE.equals(tag.getIsIndustry()))
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
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

    private record Applicability(String mode, Set<Long> industryTagIds) {
    }
}
