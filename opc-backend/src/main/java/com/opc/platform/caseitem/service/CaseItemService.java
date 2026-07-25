package com.opc.platform.caseitem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.caseitem.dto.CaseItemCreateDTO;
import com.opc.platform.caseitem.dto.CaseItemQueryDTO;
import com.opc.platform.caseitem.dto.CaseItemUpdateDTO;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.caseitem.vo.CaseItemDetailVO;
import com.opc.platform.caseitem.vo.CaseItemListVO;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.service.EvidenceReviewService;
import com.opc.platform.casetag.entity.CaseTag;
import com.opc.platform.casetag.mapper.CaseTagMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
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
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseItemService {

    private static final String PUBLISHED_STATUS = "published";

    private final CaseItemMapper caseItemMapper;

    private final RegionMapper regionMapper;

    private final SourceMapper sourceMapper;

    private final TagMapper tagMapper;

    private final CaseTagMapper caseTagMapper;

    private final EvidenceReviewService evidenceReviewService;

    @Transactional
    public CaseItemDetailVO createCaseItem(CaseItemCreateDTO dto) {
        validateRegionAndSource(dto.getRegionId(), dto.getSourceId());

        CaseItem caseItem = new CaseItem();
        copyCreateFields(dto, caseItem);
        caseItemMapper.insert(caseItem);
        syncCaseTags(caseItem.getId(), caseItem.getTags(), caseItem.getCategory());
        return getCaseItemDetail(caseItem.getId());
    }

    @Transactional
    public CaseItemDetailVO updateCaseItem(Long id, CaseItemUpdateDTO dto, AuthenticatedAdmin admin) {
        CaseItem snapshot = caseItemMapper.selectById(id);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case item not found");
        }
        requireEditSnapshot(snapshot.getEvidenceRevision(), snapshot.getUpdatedAt(),
                dto.getExpectedEvidenceRevision(), dto.getExpectedUpdatedAt());
        validateRegion(dto.getRegionId());
        lockSources(snapshot.getSourceId(), dto.getSourceId());

        CaseItem caseItem = caseItemMapper.selectByIdForUpdate(id);
        if (caseItem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case item not found");
        }
        requireEditSnapshot(caseItem.getEvidenceRevision(), caseItem.getUpdatedAt(),
                dto.getExpectedEvidenceRevision(), dto.getExpectedUpdatedAt());

        boolean evidenceChanged = evidenceRelevantFieldsChanged(caseItem, dto);
        copyUpdateFields(dto, caseItem);
        if (evidenceChanged) {
            evidenceReviewService.invalidateCaseAfterEvidenceEdit(caseItem, admin);
        }
        caseItem.setUpdatedAt(null);
        requireSingleWrite(caseItemMapper.updateById(caseItem));
        syncCaseTags(id, caseItem.getTags(), caseItem.getCategory());
        return getCaseItemDetail(id);
    }

    @Transactional
    public void deleteCaseItem(Long id, Long expectedEvidenceRevision, java.time.LocalDateTime expectedUpdatedAt) {
        CaseItem snapshot = caseItemMapper.selectById(id);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case item not found");
        }
        requireEditSnapshot(snapshot.getEvidenceRevision(), snapshot.getUpdatedAt(),
                expectedEvidenceRevision, expectedUpdatedAt);
        lockSources(snapshot.getSourceId());
        CaseItem caseItem = caseItemMapper.selectByIdForUpdate(id);
        if (caseItem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case item not found");
        }
        requireEditSnapshot(caseItem.getEvidenceRevision(), caseItem.getUpdatedAt(),
                expectedEvidenceRevision, expectedUpdatedAt);
        evidenceReviewService.requireReviewedItemDeletionAllowed("case", caseItem.getAiEvidenceStatus());
        caseTagMapper.delete(new LambdaQueryWrapper<CaseTag>().eq(CaseTag::getCaseId, id));
        requireSingleWrite(caseItemMapper.deleteById(id));
    }

    public List<CaseItemListVO> listCaseItems(CaseItemQueryDTO query) {
        List<CaseItem> caseItems = caseItemMapper.selectList(buildQueryWrapper(query)
                .orderByDesc(CaseItem::getAccessedAt)
                .orderByDesc(CaseItem::getId));

        Map<Long, Region> regionMap = loadRegionMap(caseItems);
        Map<Long, Source> sourceMap = loadSourceMap(caseItems);

        return caseItems.stream()
                .map(caseItem -> toListVO(caseItem, regionMap, sourceMap))
                .toList();
    }

    public List<CaseItemListVO> listPublicCaseItems(CaseItemQueryDTO query) {
        CaseItemQueryDTO publicQuery = new CaseItemQueryDTO();
        if (query != null) {
            publicQuery.setKeyword(query.getKeyword());
            publicQuery.setRegionId(query.getRegionId());
            publicQuery.setCategory(query.getCategory());
        }
        publicQuery.setStatus(PUBLISHED_STATUS);
        return listCaseItems(publicQuery);
    }

    public CaseItemDetailVO getCaseItemDetail(Long id) {
        CaseItem caseItem = caseItemMapper.selectById(id);
        if (caseItem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case item not found");
        }

        Region region = regionMapper.selectById(caseItem.getRegionId());
        Source source = sourceMapper.selectById(caseItem.getSourceId());
        return toDetailVO(caseItem, region, source);
    }

    public CaseItemDetailVO getPublicCaseItemDetail(Long id) {
        CaseItem caseItem = caseItemMapper.selectById(id);
        if (caseItem == null || !PUBLISHED_STATUS.equals(caseItem.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case item not found");
        }

        Region region = regionMapper.selectById(caseItem.getRegionId());
        Source source = sourceMapper.selectById(caseItem.getSourceId());
        return toDetailVO(caseItem, region, source);
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
            throw new BusinessException(ErrorCode.CONFLICT, "案例已被其他操作修改，请重新加载后再保存");
        }
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "案例已被其他操作修改，请重新加载后重试");
        }
    }

    private void copyCreateFields(CaseItemCreateDTO dto, CaseItem caseItem) {
        caseItem.setTitle(dto.getTitle());
        caseItem.setRegionId(dto.getRegionId());
        caseItem.setCategory(dto.getCategory());
        caseItem.setActorName(dto.getActorName());
        caseItem.setSourceId(dto.getSourceId());
        caseItem.setSummary(dto.getSummary());
        caseItem.setBusinessModel(dto.getBusinessModel());
        caseItem.setAiTools(dto.getAiTools());
        caseItem.setOutcome(dto.getOutcome());
        caseItem.setTags(dto.getTags());
        caseItem.setOriginalUrl(dto.getOriginalUrl());
        caseItem.setLocalFile(dto.getLocalFile());
        caseItem.setAccessedAt(dto.getAccessedAt());
        caseItem.setStatus(dto.getStatus());
        caseItem.setReviewer(dto.getReviewer());
        caseItem.setAiEvidenceStatus("legacy_unverified");
        caseItem.setEvidenceRevision(0L);
    }

    private void copyUpdateFields(CaseItemUpdateDTO dto, CaseItem caseItem) {
        caseItem.setTitle(dto.getTitle());
        caseItem.setRegionId(dto.getRegionId());
        caseItem.setCategory(dto.getCategory());
        caseItem.setActorName(dto.getActorName());
        caseItem.setSourceId(dto.getSourceId());
        caseItem.setSummary(dto.getSummary());
        caseItem.setBusinessModel(dto.getBusinessModel());
        caseItem.setAiTools(dto.getAiTools());
        caseItem.setOutcome(dto.getOutcome());
        caseItem.setTags(dto.getTags());
        caseItem.setOriginalUrl(dto.getOriginalUrl());
        caseItem.setLocalFile(dto.getLocalFile());
        caseItem.setAccessedAt(dto.getAccessedAt());
        caseItem.setStatus(dto.getStatus());
        caseItem.setReviewer(dto.getReviewer());
    }

    private boolean evidenceRelevantFieldsChanged(CaseItem current, CaseItemUpdateDTO dto) {
        return !Objects.equals(current.getTitle(), dto.getTitle())
                || !Objects.equals(current.getRegionId(), dto.getRegionId())
                || !Objects.equals(current.getCategory(), dto.getCategory())
                || !Objects.equals(current.getActorName(), dto.getActorName())
                || !Objects.equals(current.getSourceId(), dto.getSourceId())
                || !Objects.equals(current.getSummary(), dto.getSummary())
                || !Objects.equals(current.getBusinessModel(), dto.getBusinessModel())
                || !Objects.equals(current.getAiTools(), dto.getAiTools())
                || !Objects.equals(current.getOutcome(), dto.getOutcome())
                || !Objects.equals(current.getTags(), dto.getTags())
                || !Objects.equals(current.getOriginalUrl(), dto.getOriginalUrl())
                || !Objects.equals(current.getLocalFile(), dto.getLocalFile())
                || !Objects.equals(current.getAccessedAt(), dto.getAccessedAt())
                || !Objects.equals(current.getStatus(), dto.getStatus());
    }

    private LambdaQueryWrapper<CaseItem> buildQueryWrapper(CaseItemQueryDTO query) {
        LambdaQueryWrapper<CaseItem> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper;
        }

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(item -> item
                    .like(CaseItem::getTitle, query.getKeyword())
                    .or()
                    .like(CaseItem::getSummary, query.getKeyword())
                    .or()
                    .like(CaseItem::getTags, query.getKeyword())
                    .or()
                    .like(CaseItem::getActorName, query.getKeyword()));
        }
        if (query.getRegionId() != null) {
            wrapper.eq(CaseItem::getRegionId, query.getRegionId());
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(CaseItem::getCategory, query.getCategory());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(CaseItem::getStatus, query.getStatus());
        }
        return wrapper;
    }

    private Map<Long, Region> loadRegionMap(List<CaseItem> caseItems) {
        List<Long> regionIds = caseItems.stream()
                .map(CaseItem::getRegionId)
                .distinct()
                .toList();
        if (regionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return regionMapper.selectBatchIds(regionIds).stream()
                .collect(Collectors.toMap(Region::getId, Function.identity()));
    }

    private Map<Long, Source> loadSourceMap(List<CaseItem> caseItems) {
        List<Long> sourceIds = caseItems.stream()
                .map(CaseItem::getSourceId)
                .distinct()
                .toList();
        if (sourceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sourceMapper.selectBatchIds(sourceIds).stream()
                .collect(Collectors.toMap(Source::getId, Function.identity()));
    }

    private CaseItemListVO toListVO(CaseItem caseItem, Map<Long, Region> regionMap, Map<Long, Source> sourceMap) {
        Region region = regionMap.get(caseItem.getRegionId());
        Source source = sourceMap.get(caseItem.getSourceId());

        CaseItemListVO vo = new CaseItemListVO();
        vo.setId(caseItem.getId());
        vo.setTitle(caseItem.getTitle());
        vo.setRegionId(caseItem.getRegionId());
        vo.setRegionName(region == null ? null : region.getName());
        vo.setCategory(caseItem.getCategory());
        vo.setActorName(caseItem.getActorName());
        vo.setSourceId(caseItem.getSourceId());
        vo.setSourceTitle(source == null ? null : source.getTitle());
        vo.setSummary(caseItem.getSummary());
        vo.setTags(caseItem.getTags());
        vo.setAccessedAt(caseItem.getAccessedAt());
        vo.setStatus(caseItem.getStatus());
        vo.setAiEvidenceStatus(caseItem.getAiEvidenceStatus());
        vo.setEvidenceRevision(caseItem.getEvidenceRevision());
        vo.setUpdatedAt(caseItem.getUpdatedAt());
        return vo;
    }

    private CaseItemDetailVO toDetailVO(CaseItem caseItem, Region region, Source source) {
        CaseItemDetailVO vo = new CaseItemDetailVO();
        vo.setId(caseItem.getId());
        vo.setTitle(caseItem.getTitle());
        vo.setRegionId(caseItem.getRegionId());
        vo.setRegionName(region == null ? null : region.getName());
        vo.setCategory(caseItem.getCategory());
        vo.setActorName(caseItem.getActorName());
        vo.setSourceId(caseItem.getSourceId());
        vo.setSourceTitle(source == null ? null : source.getTitle());
        vo.setSummary(caseItem.getSummary());
        vo.setBusinessModel(caseItem.getBusinessModel());
        vo.setAiTools(caseItem.getAiTools());
        vo.setOutcome(caseItem.getOutcome());
        vo.setTags(caseItem.getTags());
        vo.setOriginalUrl(caseItem.getOriginalUrl());
        vo.setLocalFile(caseItem.getLocalFile());
        vo.setAccessedAt(caseItem.getAccessedAt());
        vo.setStatus(caseItem.getStatus());
        vo.setReviewer(caseItem.getReviewer());
        vo.setAiEvidenceStatus(caseItem.getAiEvidenceStatus());
        vo.setEvidenceRevision(caseItem.getEvidenceRevision());
        vo.setUpdatedAt(caseItem.getUpdatedAt());
        return vo;
    }

    private void syncCaseTags(Long caseId, String tagsText, String category) {
        caseTagMapper.delete(new LambdaQueryWrapper<CaseTag>().eq(CaseTag::getCaseId, caseId));
        Set<String> names = parseTagNames(tagsText);
        if (StringUtils.hasText(category)) {
            names.add(category.trim());
        }
        for (String name : names) {
            boolean industry = StringUtils.hasText(category) && category.trim().equals(name);
            Tag tag = getOrCreateCaseTag(name, industry);
            CaseTag relation = new CaseTag();
            relation.setCaseId(caseId);
            relation.setTagId(tag.getId());
            caseTagMapper.insert(relation);
        }
    }

    private Tag getOrCreateCaseTag(String name, boolean industry) {
        Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, name)
                .eq(Tag::getTagType, "case"));
        if (tag == null) {
            tag = new Tag();
            tag.setName(name);
            tag.setTagType("case");
            tag.setIsIndustry(industry);
            tag.setSortOrder(0);
            tagMapper.insert(tag);
            return tagMapper.selectById(tag.getId());
        }
        if (industry && !Boolean.TRUE.equals(tag.getIsIndustry())) {
            tag.setIsIndustry(true);
            tagMapper.updateById(tag);
        }
        return tag;
    }

    private Set<String> parseTagNames(String tagsText) {
        if (!StringUtils.hasText(tagsText)) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(tagsText.split("[,，]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
