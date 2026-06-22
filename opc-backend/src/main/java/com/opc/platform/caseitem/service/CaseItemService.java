package com.opc.platform.caseitem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.caseitem.dto.CaseItemCreateDTO;
import com.opc.platform.caseitem.dto.CaseItemQueryDTO;
import com.opc.platform.caseitem.dto.CaseItemUpdateDTO;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.caseitem.vo.CaseItemDetailVO;
import com.opc.platform.caseitem.vo.CaseItemListVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
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
public class CaseItemService {

    private final CaseItemMapper caseItemMapper;

    private final RegionMapper regionMapper;

    private final SourceMapper sourceMapper;

    public CaseItemDetailVO createCaseItem(CaseItemCreateDTO dto) {
        validateRegionAndSource(dto.getRegionId(), dto.getSourceId());

        CaseItem caseItem = new CaseItem();
        copyCreateFields(dto, caseItem);
        caseItemMapper.insert(caseItem);
        return getCaseItemDetail(caseItem.getId());
    }

    public CaseItemDetailVO updateCaseItem(Long id, CaseItemUpdateDTO dto) {
        CaseItem caseItem = caseItemMapper.selectById(id);
        if (caseItem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case item not found");
        }
        validateRegionAndSource(dto.getRegionId(), dto.getSourceId());

        copyUpdateFields(dto, caseItem);
        caseItemMapper.updateById(caseItem);
        return getCaseItemDetail(id);
    }

    public void deleteCaseItem(Long id) {
        CaseItem caseItem = caseItemMapper.selectById(id);
        if (caseItem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case item not found");
        }
        caseItemMapper.deleteById(id);
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

    public CaseItemDetailVO getCaseItemDetail(Long id) {
        CaseItem caseItem = caseItemMapper.selectById(id);
        if (caseItem == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case item not found");
        }

        Region region = regionMapper.selectById(caseItem.getRegionId());
        Source source = sourceMapper.selectById(caseItem.getSourceId());
        return toDetailVO(caseItem, region, source);
    }

    private void validateRegionAndSource(Long regionId, Long sourceId) {
        if (regionMapper.selectById(regionId) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Region not found");
        }
        if (sourceMapper.selectById(sourceId) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Source not found");
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
        return vo;
    }
}
