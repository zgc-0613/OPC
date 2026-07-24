package com.opc.platform.source.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.service.EvidenceReviewService;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.source.dto.SourceCreateDTO;
import com.opc.platform.source.dto.SourceUpdateDTO;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.source.vo.SourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.net.URI;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SourceService {

    private static final String PUBLISHED_STATUS = "published";
    private static final Set<String> ALLOWED_STATUSES = Set.of("draft", "pending", PUBLISHED_STATUS, "archived");

    private final SourceMapper sourceMapper;

    private final EvidenceReviewService evidenceReviewService;

    @Transactional
    public SourceVO createSource(SourceCreateDTO dto) {
        validateUniqueTitle(dto.getTitle(), null);
        validateSourceUrl(dto.getUrl());
        Source source = new Source();
        copyCreateFields(dto, source);
        sourceMapper.insert(source);
        return toVO(sourceMapper.selectById(source.getId()));
    }

    @Transactional
    public SourceVO updateSource(Long id, SourceUpdateDTO dto, AuthenticatedAdmin admin) {
        Source source = sourceMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Source not found");
        }
        validateUniqueTitle(dto.getTitle(), id);
        validateSourceUrl(dto.getUrl());
        boolean evidenceChanged = evidenceRelevantFieldsChanged(source, dto);
        copyUpdateFields(dto, source);
        if (evidenceChanged) {
            evidenceReviewService.invalidateSourceAfterEvidenceEdit(source, admin);
        }
        sourceMapper.updateById(source);
        return toVO(sourceMapper.selectById(id));
    }

    @Transactional
    public void deleteSource(Long id) {
        Source source = sourceMapper.selectById(id);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Source not found");
        }
        evidenceReviewService.requireSourceDeletionAllowed(source);
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
        source.setStatus(normalizeStatus(dto.getStatus()));
        source.setAiEvidenceStatus("legacy_unverified");
        source.setEvidenceRevision(0L);
    }

    private void copyUpdateFields(SourceUpdateDTO dto, Source source) {
        source.setTitle(dto.getTitle().trim());
        source.setSourceType(dto.getSourceType());
        source.setPublisher(dto.getPublisher());
        source.setUrl(dto.getUrl());
        source.setLocalFile(dto.getLocalFile());
        source.setAccessedAt(dto.getAccessedAt());
        source.setNotes(dto.getNotes());
        source.setStatus(normalizeStatus(dto.getStatus()));
    }

    private boolean evidenceRelevantFieldsChanged(Source current, SourceUpdateDTO dto) {
        return !Objects.equals(current.getTitle(), dto.getTitle().trim())
                || !Objects.equals(current.getSourceType(), dto.getSourceType())
                || !Objects.equals(current.getPublisher(), dto.getPublisher())
                || !Objects.equals(current.getUrl(), dto.getUrl())
                || !Objects.equals(current.getLocalFile(), dto.getLocalFile())
                || !Objects.equals(current.getAccessedAt(), dto.getAccessedAt())
                || !Objects.equals(current.getNotes(), dto.getNotes())
                || !Objects.equals(current.getStatus(), normalizeStatus(dto.getStatus()));
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

    private void validateSourceUrl(String value) {
        if (!StringUtils.hasText(value)) return;
        try {
            URI uri = URI.create(value.trim());
            if (uri.isAbsolute()
                    && uri.getUserInfo() == null
                    && StringUtils.hasText(uri.getHost())
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // Return the same public validation error for malformed and unsafe URLs.
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "来源链接必须是安全的 HTTP/HTTPS 绝对地址");
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

    private String normalizeStatus(String requested) {
        String status = StringUtils.hasText(requested)
                ? requested.trim().toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "来源状态必须为 draft、pending、published 或 archived");
        }
        return status;
    }
}
