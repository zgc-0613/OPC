package com.opc.platform.source.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
