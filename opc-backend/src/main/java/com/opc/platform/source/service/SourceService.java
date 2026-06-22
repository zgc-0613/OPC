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

@Service
@RequiredArgsConstructor
public class SourceService {

    private final SourceMapper sourceMapper;

    public SourceVO createSource(SourceCreateDTO dto) {
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

    private void copyCreateFields(SourceCreateDTO dto, Source source) {
        source.setTitle(dto.getTitle());
        source.setSourceType(dto.getSourceType());
        source.setPublisher(dto.getPublisher());
        source.setUrl(dto.getUrl());
        source.setLocalFile(dto.getLocalFile());
        source.setAccessedAt(dto.getAccessedAt());
        source.setNotes(dto.getNotes());
        source.setStatus(dto.getStatus());
    }

    private void copyUpdateFields(SourceUpdateDTO dto, Source source) {
        source.setTitle(dto.getTitle());
        source.setSourceType(dto.getSourceType());
        source.setPublisher(dto.getPublisher());
        source.setUrl(dto.getUrl());
        source.setLocalFile(dto.getLocalFile());
        source.setAccessedAt(dto.getAccessedAt());
        source.setNotes(dto.getNotes());
        source.setStatus(dto.getStatus());
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
        return vo;
    }
}
