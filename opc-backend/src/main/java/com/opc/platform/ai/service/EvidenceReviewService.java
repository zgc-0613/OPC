package com.opc.platform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.EvidenceReviewQueryDTO;
import com.opc.platform.ai.dto.EvidenceReviewUpdateDTO;
import com.opc.platform.ai.entity.AiEvidenceReview;
import com.opc.platform.ai.mapper.AiEvidenceReviewMapper;
import com.opc.platform.ai.vo.EvidenceReviewItemVO;
import com.opc.platform.ai.vo.EvidenceReviewPageVO;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvidenceReviewService {

    private static final String PUBLISHED = "published";
    private static final String VERIFIED = "verified";

    private final CaseItemMapper caseItemMapper;
    private final PolicyMapper policyMapper;
    private final SourceMapper sourceMapper;
    private final AiEvidenceReviewMapper reviewMapper;

    public EvidenceReviewPageVO list(EvidenceReviewQueryDTO query) {
        Map<Long, Source> sources = sourceMapper.selectList(new LambdaQueryWrapper<Source>()).stream()
                .collect(Collectors.toMap(Source::getId, Function.identity(), (left, right) -> left));
        List<EvidenceReviewItemVO> values = new ArrayList<>();
        if (!StringUtils.hasText(query.getItemType()) || "case".equals(query.getItemType())) {
            caseItemMapper.selectList(new LambdaQueryWrapper<CaseItem>()).forEach(item -> values.add(caseItem(item, sources)));
        }
        if (!StringUtils.hasText(query.getItemType()) || "policy".equals(query.getItemType())) {
            policyMapper.selectList(new LambdaQueryWrapper<Policy>()).forEach(item -> values.add(policy(item, sources)));
        }
        if (!StringUtils.hasText(query.getItemType()) || "source".equals(query.getItemType())) {
            sources.values().forEach(source -> values.add(source(source)));
        }
        if (StringUtils.hasText(query.getEvidenceStatus())) {
            values.removeIf(item -> !query.getEvidenceStatus().equals(item.getEvidenceStatus()));
        }
        values.sort(Comparator.comparing(EvidenceReviewItemVO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(EvidenceReviewItemVO::getItemId, Comparator.reverseOrder()));
        int from = Math.min((query.getPage() - 1) * query.getSize(), values.size());
        int to = Math.min(from + query.getSize(), values.size());
        EvidenceReviewPageVO page = new EvidenceReviewPageVO();
        page.setItems(List.copyOf(values.subList(from, to)));
        page.setPage(query.getPage());
        page.setSize(query.getSize());
        page.setTotal(values.size());
        return page;
    }

    @Transactional
    public EvidenceReviewItemVO review(
            String itemType,
            Long itemId,
            EvidenceReviewUpdateDTO dto,
            AuthenticatedAdmin admin
    ) {
        return switch (itemType) {
            case "case" -> reviewCase(itemId, dto, admin);
            case "policy" -> reviewPolicy(itemId, dto, admin);
            case "source" -> reviewSource(itemId, dto, admin);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported evidence item type");
        };
    }

    private EvidenceReviewItemVO reviewCase(Long id, EvidenceReviewUpdateDTO dto, AuthenticatedAdmin admin) {
        CaseItem item = caseItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case not found");
        }
        Source source = item.getSourceId() == null ? null : sourceMapper.selectById(item.getSourceId());
        requireVerifiable(dto.getEvidenceStatus(), item.getStatus(), source);
        String previous = item.getAiEvidenceStatus();
        item.setAiEvidenceStatus(dto.getEvidenceStatus());
        caseItemMapper.updateById(item);
        audit("case", item.getId(), previous, dto, admin);
        return caseItem(item, source == null ? Map.of() : Map.of(source.getId(), source));
    }

    private EvidenceReviewItemVO reviewPolicy(Long id, EvidenceReviewUpdateDTO dto, AuthenticatedAdmin admin) {
        Policy item = policyMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }
        Source source = item.getSourceId() == null ? null : sourceMapper.selectById(item.getSourceId());
        requireVerifiable(dto.getEvidenceStatus(), item.getStatus(), source);
        String previous = item.getAiEvidenceStatus();
        item.setAiEvidenceStatus(dto.getEvidenceStatus());
        policyMapper.updateById(item);
        audit("policy", item.getId(), previous, dto, admin);
        return policy(item, source == null ? Map.of() : Map.of(source.getId(), source));
    }

    private EvidenceReviewItemVO reviewSource(Long id, EvidenceReviewUpdateDTO dto, AuthenticatedAdmin admin) {
        Source item = sourceMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Source not found");
        }
        if (VERIFIED.equals(dto.getEvidenceStatus())
                && (!PUBLISHED.equals(item.getStatus()) || !StringUtils.hasText(item.getTitle()) || !StringUtils.hasText(item.getUrl()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "来源必须已发布且具有标题和原始链接后才能核验");
        }
        String previous = item.getAiEvidenceStatus();
        item.setAiEvidenceStatus(dto.getEvidenceStatus());
        sourceMapper.updateById(item);
        audit("source", item.getId(), previous, dto, admin);
        return source(item);
    }

    private void requireVerifiable(String requestedStatus, String publicationStatus, Source source) {
        if (!VERIFIED.equals(requestedStatus)) {
            return;
        }
        if (!PUBLISHED.equals(publicationStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已发布资料可标记为已核验");
        }
        if (!eligibleSource(source)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "案例或政策必须关联完整、已发布、已核验的来源后才能核验");
        }
    }

    private void audit(
            String itemType,
            Long itemId,
            String previousStatus,
            EvidenceReviewUpdateDTO dto,
            AuthenticatedAdmin admin
    ) {
        AiEvidenceReview review = new AiEvidenceReview();
        review.setItemType(itemType);
        review.setItemId(itemId);
        review.setPreviousStatus(previousStatus == null ? "legacy_unverified" : previousStatus);
        review.setNewStatus(dto.getEvidenceStatus());
        review.setAdminId(admin.adminId());
        review.setAdminUsername(admin.username());
        review.setNotes(StringUtils.hasText(dto.getNotes()) ? dto.getNotes().trim() : null);
        reviewMapper.insert(review);
    }

    private EvidenceReviewItemVO caseItem(CaseItem item, Map<Long, Source> sources) {
        Source source = item.getSourceId() == null ? null : sources.get(item.getSourceId());
        return item("case", item.getId(), item.getTitle(), item.getStatus(), item.getAiEvidenceStatus(), item.getSourceId(), source, item.getUpdatedAt());
    }

    private EvidenceReviewItemVO policy(Policy item, Map<Long, Source> sources) {
        Source source = item.getSourceId() == null ? null : sources.get(item.getSourceId());
        return item("policy", item.getId(), item.getTitle(), item.getStatus(), item.getAiEvidenceStatus(), item.getSourceId(), source, item.getUpdatedAt());
    }

    private EvidenceReviewItemVO source(Source source) {
        return item("source", source.getId(), source.getTitle(), source.getStatus(), source.getAiEvidenceStatus(), null, source, source.getUpdatedAt());
    }

    private EvidenceReviewItemVO item(
            String itemType,
            Long itemId,
            String title,
            String publicationStatus,
            String evidenceStatus,
            Long sourceId,
            Source source,
            java.time.LocalDateTime updatedAt
    ) {
        EvidenceReviewItemVO result = new EvidenceReviewItemVO();
        result.setItemType(itemType);
        result.setItemId(itemId);
        result.setTitle(title);
        result.setPublicationStatus(publicationStatus);
        result.setEvidenceStatus(evidenceStatus == null ? "legacy_unverified" : evidenceStatus);
        result.setSourceId(sourceId);
        result.setSourceEligible("source".equals(itemType) ? eligibleSource(source) : eligibleSource(source));
        result.setSourceTitle(source == null ? null : source.getTitle());
        result.setSourceStatus(source == null ? null : source.getStatus());
        result.setSourceEvidenceStatus(source == null ? null : source.getAiEvidenceStatus());
        result.setUpdatedAt(updatedAt);
        return result;
    }

    private boolean eligibleSource(Source source) {
        return source != null
                && PUBLISHED.equals(source.getStatus())
                && VERIFIED.equals(source.getAiEvidenceStatus())
                && StringUtils.hasText(source.getTitle())
                && StringUtils.hasText(source.getUrl());
    }
}
