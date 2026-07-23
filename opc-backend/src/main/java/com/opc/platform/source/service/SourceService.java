package com.opc.platform.source.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.source.dto.SourceCreateDTO;
import com.opc.platform.source.dto.SourceUpdateDTO;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.source.vo.SourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SourceService {

    private static final String PUBLISHED_STATUS = "published";

    private final SourceMapper sourceMapper;

    public SourceVO createSource(SourceCreateDTO dto) {
        validateUniqueTitle(dto.getTitle(), null);
        Source source = new Source();
        copyCreateFields(dto, source);
        sourceMapper.insert(source);
        return toVO(sourceMapper.selectById(source.getId()));
    }

    public SourceVO updateSource(Long id, SourceUpdateDTO dto) {
        Source source = sourceMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Source not found");
        }
        validateUniqueTitle(dto.getTitle(), id);
        copyUpdateFields(dto, source);
        sourceMapper.updateById(source);
        return toVO(sourceMapper.selectById(id));
    }

    public void deleteSource(Long id) {
        Source source = sourceMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Source not found");
        }
        sourceMapper.deleteById(id);
    }

    public List<SourceVO> listSources() {
        List<Source> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<Source>()
                        .orderByDesc(Source::getAccessedAt)
                        .orderByDesc(Source::getId)
        );

        return sources.stream()
                .map(this::toVO)
                .toList();
    }

    public List<SourceVO> listPublicSources() {
        List<Source> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<Source>()
                        .eq(Source::getStatus, PUBLISHED_STATUS)
                        .orderByDesc(Source::getAccessedAt)
                        .orderByDesc(Source::getId)
        );

        return sources.stream()
                .map(this::toVO)
                .toList();
    }

    private void copyCreateFields(SourceCreateDTO dto, Source source) {
        source.setTitle(dto.getTitle().trim());
        source.setSourceType(dto.getSourceType());
        source.setPublisher(dto.getPublisher());
        source.setUrl(dto.getUrl());
        source.setLocalFile(dto.getLocalFile());
        source.setAccessedAt(dto.getAccessedAt());
        source.setNotes(dto.getNotes());
        source.setStatus(dto.getStatus());
        source.setAiEvidenceStatus(normalizeEvidenceStatus(dto.getAiEvidenceStatus(), "legacy_unverified"));
    }

    private void copyUpdateFields(SourceUpdateDTO dto, Source source) {
        source.setTitle(dto.getTitle().trim());
        source.setSourceType(dto.getSourceType());
        source.setPublisher(dto.getPublisher());
        source.setUrl(dto.getUrl());
        source.setLocalFile(dto.getLocalFile());
        source.setAccessedAt(dto.getAccessedAt());
        source.setNotes(dto.getNotes());
        source.setStatus(dto.getStatus());
        source.setAiEvidenceStatus(normalizeEvidenceStatus(dto.getAiEvidenceStatus(), source.getAiEvidenceStatus()));
    }

    private void validateUniqueTitle(String title, Long excludedId) {
        LambdaQueryWrapper<Source> wrapper = new LambdaQueryWrapper<Source>()
                .eq(Source::getTitle, title.trim());
        if (excludedId != null) {
            wrapper.ne(Source::getId, excludedId);
        }
        Source conflict = sourceMapper.selectOne(wrapper.last("LIMIT 1"));
        if (conflict != null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "来源名称“" + conflict.getTitle() + "”已存在（ID: " + conflict.getId() + "），请使用该来源或修改名称"
            );
        }
    }

    private SourceVO toVO(Source source) {
        SourceVO vo = new SourceVO();
        vo.setId(source.getId());
        vo.setTitle(source.getTitle());
        vo.setSourceType(source.getSourceType());
        vo.setPublisher(source.getPublisher());
        vo.setUrl(source.getUrl());
        vo.setLocalFile(source.getLocalFile());
        vo.setAccessedAt(source.getAccessedAt());
        vo.setNotes(source.getNotes());
        vo.setStatus(source.getStatus());
        vo.setAiEvidenceStatus(source.getAiEvidenceStatus());
        return vo;
    }

    private String normalizeEvidenceStatus(String requested, String fallback) {
        return StringUtils.hasText(requested) ? requested.trim() : fallback;
    }
}
