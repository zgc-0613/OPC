package com.opc.platform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.EvidenceReviewBatchItemDTO;
import com.opc.platform.ai.dto.EvidenceReviewBatchUpdateDTO;
import com.opc.platform.ai.dto.EvidenceReviewQueryDTO;
import com.opc.platform.ai.dto.EvidenceReviewUpdateDTO;
import com.opc.platform.ai.entity.AiEvidenceReview;
import com.opc.platform.ai.mapper.AiEvidenceReviewMapper;
import com.opc.platform.ai.mapper.EvidenceReviewQueueMapper;
import com.opc.platform.ai.mapper.EvidenceReviewQueueRow;
import com.opc.platform.ai.vo.EvidenceReviewItemVO;
import com.opc.platform.ai.vo.EvidenceReviewBatchResultVO;
import com.opc.platform.ai.vo.EvidenceReviewCheckVO;
import com.opc.platform.ai.vo.EvidenceReviewDetailVO;
import com.opc.platform.ai.vo.EvidenceReviewHistoryVO;
import com.opc.platform.ai.vo.EvidenceReviewPageVO;
import com.opc.platform.ai.vo.EvidenceReviewPreflightItemVO;
import com.opc.platform.ai.vo.EvidenceReviewPreflightVO;
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
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EvidenceReviewService {

    private static final String PUBLISHED = "published";
    private static final String VERIFIED = "verified";
    private final CaseItemMapper caseItemMapper;
    private final PolicyMapper policyMapper;
    private final SourceMapper sourceMapper;
    private final AiEvidenceReviewMapper reviewMapper;
    private final EvidenceReviewQueueMapper queueMapper;

    public EvidenceReviewPageVO list(EvidenceReviewQueryDTO query) {
        EvidenceReviewPageVO page = new EvidenceReviewPageVO();
        int offset = Math.multiplyExact(query.getPage() - 1, query.getSize());
        List<EvidenceReviewQueueRow> rows = safe(queueMapper.selectPage(query, query.getSize(), offset));
        page.setItems(rows.stream().map(this::queueItem).toList());
        page.setPage(query.getPage());
        page.setSize(query.getSize());
        page.setTotal(queueMapper.count(query));
        return page;
    }

    private EvidenceReviewItemVO queueItem(EvidenceReviewQueueRow row) {
        EvidenceReviewItemVO item = new EvidenceReviewItemVO();
        item.setItemType(row.getItemType());
        item.setItemId(row.getItemId());
        item.setTitle(row.getTitle());
        item.setPublicationStatus(row.getPublicationStatus());
        item.setEvidenceStatus(row.getEvidenceStatus());
        item.setSourceId(row.getSourceId());
        item.setSourceTitle(row.getSourceTitle());
        item.setSourceStatus(row.getSourceStatus());
        item.setSourceEvidenceStatus(row.getSourceEvidenceStatus());
        item.setSourceEligible(row.getSourceId() != null && "published".equals(row.getSourceStatus())
                && "verified".equals(row.getSourceEvidenceStatus()));
        item.setReviewable(row.isReviewable());
        item.setVersion(row.getVersion());
        item.setUpdatedAt(row.getUpdatedAt());
        List<String> blockers = new ArrayList<>();
        if (!PUBLISHED.equals(row.getPublicationStatus())) blockers.add("资料尚未发布");
        if (!row.isContentComplete()) blockers.add("资料缺少标题、摘要或必要发布信息");
        if ("source".equals(row.getItemType())) {
            if (!StringUtils.hasText(row.getSourcePublisher())) blockers.add("来源缺少发布机构");
            if (!safeEvidenceUrl(row.getSourceUrl())) blockers.add("来源缺少安全原文链接");
        } else {
            if (row.getSourceId() == null) blockers.add("资料未关联来源");
            if (!"published".equals(row.getSourceStatus())) blockers.add("关联来源尚未发布");
            if (!"verified".equals(row.getSourceEvidenceStatus())) blockers.add("关联来源尚未核验");
            if (!safeEvidenceUrl(row.getSourceUrl())) blockers.add("关联来源缺少安全原文链接");
        }
        item.setBlockingReasons(List.copyOf(blockers));
        return item;
    }

    private boolean safeEvidenceUrl(String value) {
        return EvidenceUrlPolicy.isSafe(value);
    }

    public EvidenceReviewDetailVO detail(String itemType, Long itemId) {
        return switch (itemType) {
            case "case" -> caseDetail(itemId);
            case "policy" -> policyDetail(itemId);
            case "source" -> sourceDetail(itemId);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported evidence item type");
        };
    }

    public EvidenceReviewPreflightVO preflight(EvidenceReviewBatchUpdateDTO dto) {
        Set<Long> sourcesVerifiedInBatch = new HashSet<>();
        Set<Long> selectedCaseIds = dto.getItems().stream()
                .filter(item -> "case".equals(item.getItemType()))
                .map(EvidenceReviewBatchItemDTO::getItemId)
                .collect(Collectors.toSet());
        Set<Long> selectedPolicyIds = dto.getItems().stream()
                .filter(item -> "policy".equals(item.getItemType()))
                .map(EvidenceReviewBatchItemDTO::getItemId)
                .collect(Collectors.toSet());
        if (VERIFIED.equals(dto.getEvidenceStatus())) {
            for (EvidenceReviewBatchItemDTO target : dto.getItems()) {
                if (!"source".equals(target.getItemType())) continue;
                try {
                    EvidenceReviewDetailVO sourceDetail = detail("source", target.getItemId());
                    if (sourceDetail.isReviewable()
                            && normalizeEvidenceStatus(sourceDetail.getEvidenceStatus()).equals(target.getExpectedEvidenceStatus())
                            && java.util.Objects.equals(sourceDetail.getVersion(), target.getExpectedVersion())
                            && sameUpdatedAt(sourceDetail.getUpdatedAt(), target.getExpectedUpdatedAt())) {
                        sourcesVerifiedInBatch.add(target.getItemId());
                    }
                } catch (BusinessException ignored) {
                    // The regular item preflight below reports a safe, item-scoped blocker.
                }
            }
        }
        List<EvidenceReviewPreflightItemVO> items = dto.getItems().stream()
                .map(target -> preflightItem(target, dto.getEvidenceStatus(), dto.isCascade(), sourcesVerifiedInBatch,
                        selectedCaseIds, selectedPolicyIds))
                .toList();
        EvidenceReviewPreflightVO result = new EvidenceReviewPreflightVO();
        result.setRequestedCount(items.size());
        result.setActionableCount((int) items.stream().filter(EvidenceReviewPreflightItemVO::isAllowed).count());
        result.setBlockedCount(result.getRequestedCount() - result.getActionableCount());
        result.setAffectedCaseCount(items.stream().mapToInt(EvidenceReviewPreflightItemVO::getAffectedCaseCount).sum());
        result.setAffectedPolicyCount(items.stream().mapToInt(EvidenceReviewPreflightItemVO::getAffectedPolicyCount).sum());
        result.setItems(items);
        return result;
    }

    private EvidenceReviewDetailVO caseDetail(Long id) {
        CaseItem item = caseItemMapper.selectById(id);
        if (item == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Case not found");
        Source source = item.getSourceId() == null ? null : sourceMapper.selectById(item.getSourceId());
        return detail("case", item.getId(), item.getTitle(), item.getStatus(), item.getAiEvidenceStatus(),
                item.getSourceId(), source, item, item.getOriginalUrl(), revision(item.getEvidenceRevision()), item.getUpdatedAt());
    }

    private EvidenceReviewDetailVO policyDetail(Long id) {
        Policy item = policyMapper.selectById(id);
        if (item == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        Source source = item.getSourceId() == null ? null : sourceMapper.selectById(item.getSourceId());
        String originalUrl = StringUtils.hasText(item.getOriginalUrl()) ? item.getOriginalUrl() : item.getEvidenceUrl();
        return detail("policy", item.getId(), item.getTitle(), item.getStatus(), item.getAiEvidenceStatus(),
                item.getSourceId(), source, item, originalUrl, revision(item.getEvidenceRevision()), item.getUpdatedAt());
    }

    private EvidenceReviewDetailVO sourceDetail(Long id) {
        Source item = sourceMapper.selectById(id);
        if (item == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Source not found");
        return detail("source", item.getId(), item.getTitle(), item.getStatus(), item.getAiEvidenceStatus(),
                item.getId(), item, item, item.getUrl(), revision(item.getEvidenceRevision()), item.getUpdatedAt());
    }

    private EvidenceReviewDetailVO detail(
            String itemType,
            Long itemId,
            String title,
            String publicationStatus,
            String evidenceStatus,
            Long sourceId,
            Source source,
            Object content,
            String originalUrl,
            Long version,
            LocalDateTime updatedAt
    ) {
        ReviewEvaluation evaluation = evaluate(itemType, publicationStatus, source, content);
        EvidenceReviewDetailVO result = new EvidenceReviewDetailVO();
        result.setItemType(itemType);
        result.setItemId(itemId);
        result.setTitle(title);
        result.setPublicationStatus(publicationStatus);
        result.setEvidenceStatus(normalizeEvidenceStatus(evidenceStatus));
        result.setSourceId(sourceId);
        result.setSourceTitle(source == null ? null : source.getTitle());
        result.setReviewable(evaluation.blockingReasons().isEmpty());
        result.setBlockingReasons(evaluation.blockingReasons());
        result.setChecks(evaluation.checks());
        result.setContent(content);
        result.setSource(source);
        result.setRelatedItems(loadRelatedItems(sourceId));
        result.setHistory(loadHistory(itemType, itemId));
        result.setVersion(version);
        result.setUpdatedAt(updatedAt);
        result.setOriginalUrl(originalUrl);
        return result;
    }

    private List<EvidenceReviewItemVO> loadRelatedItems(Long sourceId) {
        if (sourceId == null) return List.of();
        Source source = sourceMapper.selectById(sourceId);
        Map<Long, Source> sources = source == null ? Map.of() : Map.of(sourceId, source);
        List<EvidenceReviewItemVO> related = new ArrayList<>();
        safe(caseItemMapper.selectList(new LambdaQueryWrapper<CaseItem>().eq(CaseItem::getSourceId, sourceId)))
                .forEach(item -> related.add(caseItem(item, sources)));
        safe(policyMapper.selectList(new LambdaQueryWrapper<Policy>().eq(Policy::getSourceId, sourceId)))
                .forEach(item -> related.add(policy(item, sources)));
        return List.copyOf(related);
    }

    private List<EvidenceReviewHistoryVO> loadHistory(String itemType, Long itemId) {
        return safe(reviewMapper.selectList(new LambdaQueryWrapper<AiEvidenceReview>()
                .eq(AiEvidenceReview::getItemType, itemType)
                .eq(AiEvidenceReview::getItemId, itemId)
                .orderByDesc(AiEvidenceReview::getCreatedAt)
                .orderByDesc(AiEvidenceReview::getId)))
                .stream()
                .map(this::history)
                .toList();
    }

    private EvidenceReviewHistoryVO history(AiEvidenceReview audit) {
        EvidenceReviewHistoryVO value = new EvidenceReviewHistoryVO();
        value.setId(audit.getId());
        value.setPreviousStatus(audit.getPreviousStatus());
        value.setNewStatus(audit.getNewStatus());
        value.setAdminUsername(audit.getAdminUsername());
        value.setActionType(audit.getActionType());
        value.setReason(audit.getReason());
        value.setNotes(audit.getNotes());
        value.setOperationId(audit.getOperationId());
        value.setCreatedAt(audit.getCreatedAt());
        return value;
    }

    private EvidenceReviewPreflightItemVO preflightItem(
            EvidenceReviewBatchItemDTO target,
            String targetStatus,
            boolean cascade,
            Set<Long> sourcesVerifiedInBatch,
            Set<Long> selectedCaseIds,
            Set<Long> selectedPolicyIds
    ) {
        EvidenceReviewPreflightItemVO result = new EvidenceReviewPreflightItemVO();
        result.setItemType(target.getItemType());
        result.setItemId(target.getItemId());
        List<String> blockers = new ArrayList<>();
        try {
            EvidenceReviewDetailVO detail = detail(target.getItemType(), target.getItemId());
            result.setTitle(detail.getTitle());
            if (!normalizeEvidenceStatus(detail.getEvidenceStatus()).equals(target.getExpectedEvidenceStatus())
                    || !java.util.Objects.equals(detail.getVersion(), target.getExpectedVersion())
                    || !sameUpdatedAt(detail.getUpdatedAt(), target.getExpectedUpdatedAt())) {
                blockers.add("资料已被其他管理员修改，请刷新后重试");
            }
            if (normalizeEvidenceStatus(detail.getEvidenceStatus()).equals(targetStatus)) {
                blockers.add("资料已经处于目标状态");
            }
            if (VERIFIED.equals(targetStatus)) {
                boolean sourceWillBeVerified = !"source".equals(target.getItemType())
                        && detail.getSourceId() != null
                        && sourcesVerifiedInBatch.contains(detail.getSourceId());
                detail.getChecks().stream()
                        .filter(check -> !check.isPassed())
                        .filter(check -> !(sourceWillBeVerified && "source_verified".equals(check.getKey())))
                        .map(EvidenceReviewCheckVO::getMessage)
                        .forEach(blockers::add);
            }
            if ("source".equals(target.getItemType()) && !VERIFIED.equals(targetStatus)) {
                int caseCount = verifiedCaseDependencyCount(target.getItemId(), selectedCaseIds);
                int policyCount = verifiedPolicyDependencyCount(target.getItemId(), selectedPolicyIds);
                result.setAffectedCaseCount(caseCount);
                result.setAffectedPolicyCount(policyCount);
                if ((caseCount + policyCount) > 0 && !cascade) {
                    blockers.add("该来源仍被已核验案例或政策使用，请确认级联后再操作");
                }
            }
        } catch (BusinessException exception) {
            blockers.add(exception.getErrorCode() == ErrorCode.NOT_FOUND ? "资料不存在" : exception.getMessage());
        }
        result.setAllowed(blockers.isEmpty());
        result.setBlockingReasons(List.copyOf(new LinkedHashSet<>(blockers)));
        return result;
    }

    private ReviewEvaluation evaluate(String itemType, String publicationStatus, Source source, Object content) {
        List<EvidenceReviewCheckVO> checks = new ArrayList<>();
        checks.add(check("published", "资料已发布", PUBLISHED.equals(publicationStatus), "资料必须处于已发布状态"));
        if ("source".equals(itemType)) {
            checks.add(check("source_title", "来源标题完整", source != null && StringUtils.hasText(source.getTitle()), "来源缺少标题"));
            checks.add(check("source_publisher", "发布机构完整", source != null && StringUtils.hasText(source.getPublisher()), "来源缺少发布机构"));
            checks.add(check("source_url", "原文链接安全且完整", source != null && isSafeEvidenceUrl(source.getUrl()), "来源必须提供安全的 HTTP/HTTPS 原文链接"));
        } else {
            boolean contentComplete = content instanceof CaseItem caseItem
                    ? StringUtils.hasText(caseItem.getTitle()) && StringUtils.hasText(caseItem.getSummary())
                    : content instanceof Policy policy
                            && StringUtils.hasText(policy.getTitle())
                            && StringUtils.hasText(policy.getSummary())
                            && StringUtils.hasText(policy.getIssuingBody());
            checks.add(check("content_complete", "资料核心内容完整", contentComplete, "资料缺少标题、摘要或必要发布信息"));
            checks.add(check("source_linked", "已关联来源", source != null, "资料未关联来源"));
            checks.add(check("source_published", "来源已发布", source != null && PUBLISHED.equals(source.getStatus()), "关联来源尚未发布"));
            checks.add(check("source_verified", "来源已核验", source != null && VERIFIED.equals(source.getAiEvidenceStatus()), "关联来源尚未核验"));
            checks.add(check("source_url", "来源原文链接安全且完整", source != null && isSafeEvidenceUrl(source.getUrl()), "关联来源必须提供安全的 HTTP/HTTPS 原文链接"));
        }
        List<String> blockers = checks.stream().filter(check -> !check.isPassed()).map(EvidenceReviewCheckVO::getMessage).toList();
        return new ReviewEvaluation(List.copyOf(checks), blockers);
    }

    private EvidenceReviewCheckVO check(String key, String label, boolean passed, String message) {
        return new EvidenceReviewCheckVO(key, label, passed, passed ? "条件已满足" : message);
    }

    private int verifiedCaseDependencyCount(Long sourceId) {
        return verifiedCaseDependencyCount(sourceId, Set.of());
    }

    private int verifiedCaseDependencyCount(Long sourceId, Set<Long> excludedIds) {
        LambdaQueryWrapper<CaseItem> query = new LambdaQueryWrapper<CaseItem>()
                .eq(CaseItem::getSourceId, sourceId)
                .eq(CaseItem::getAiEvidenceStatus, VERIFIED);
        if (excludedIds != null && !excludedIds.isEmpty()) query.notIn(CaseItem::getId, excludedIds);
        Long count = caseItemMapper.selectCount(query);
        return count == null ? 0 : Math.toIntExact(count);
    }

    private int verifiedPolicyDependencyCount(Long sourceId) {
        return verifiedPolicyDependencyCount(sourceId, Set.of());
    }

    private int verifiedPolicyDependencyCount(Long sourceId, Set<Long> excludedIds) {
        LambdaQueryWrapper<Policy> query = new LambdaQueryWrapper<Policy>()
                .eq(Policy::getSourceId, sourceId)
                .eq(Policy::getAiEvidenceStatus, VERIFIED);
        if (excludedIds != null && !excludedIds.isEmpty()) query.notIn(Policy::getId, excludedIds);
        Long count = policyMapper.selectCount(query);
        return count == null ? 0 : Math.toIntExact(count);
    }

    private String normalizeEvidenceStatus(String value) {
        return StringUtils.hasText(value) ? value : "legacy_unverified";
    }

    private boolean sameUpdatedAt(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) return left == null && right == null;
        return left.equals(right);
    }

    private long revision(Long value) {
        return value == null ? 0L : value;
    }

    private record ReviewEvaluation(List<EvidenceReviewCheckVO> checks, List<String> blockingReasons) {}

    private LambdaQueryWrapper<CaseItem> caseQuery(EvidenceReviewQueryDTO query, boolean ordered) {
        LambdaQueryWrapper<CaseItem> wrapper = new LambdaQueryWrapper<>();
        applyEvidenceStatus(wrapper, query.getEvidenceStatus(), CaseItem::getAiEvidenceStatus);
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(value -> value.like(CaseItem::getTitle, query.getKeyword().trim())
                    .or().like(CaseItem::getSummary, query.getKeyword().trim())
                    .or().like(CaseItem::getTags, query.getKeyword().trim()));
        }
        if (query.getSourceId() != null) wrapper.eq(CaseItem::getSourceId, query.getSourceId());
        if (ordered) wrapper.orderByDesc(CaseItem::getUpdatedAt).orderByDesc(CaseItem::getId);
        return wrapper;
    }

    private LambdaQueryWrapper<Policy> policyQuery(EvidenceReviewQueryDTO query, boolean ordered) {
        LambdaQueryWrapper<Policy> wrapper = new LambdaQueryWrapper<>();
        applyEvidenceStatus(wrapper, query.getEvidenceStatus(), Policy::getAiEvidenceStatus);
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(value -> value.like(Policy::getTitle, query.getKeyword().trim())
                    .or().like(Policy::getSummary, query.getKeyword().trim())
                    .or().like(Policy::getTags, query.getKeyword().trim()));
        }
        if (query.getSourceId() != null) wrapper.eq(Policy::getSourceId, query.getSourceId());
        if (ordered) wrapper.orderByDesc(Policy::getUpdatedAt).orderByDesc(Policy::getId);
        return wrapper;
    }

    private LambdaQueryWrapper<Source> sourceQuery(EvidenceReviewQueryDTO query, boolean ordered) {
        LambdaQueryWrapper<Source> wrapper = new LambdaQueryWrapper<>();
        applyEvidenceStatus(wrapper, query.getEvidenceStatus(), Source::getAiEvidenceStatus);
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(value -> value.like(Source::getTitle, query.getKeyword().trim())
                    .or().like(Source::getPublisher, query.getKeyword().trim())
                    .or().like(Source::getNotes, query.getKeyword().trim()));
        }
        if (query.getSourceId() != null) wrapper.eq(Source::getId, query.getSourceId());
        if (ordered) wrapper.orderByDesc(Source::getUpdatedAt).orderByDesc(Source::getId);
        return wrapper;
    }

    private Comparator<EvidenceReviewItemVO> reviewComparator(String sort) {
        Comparator<EvidenceReviewItemVO> updated = Comparator.comparing(
                EvidenceReviewItemVO::getUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
        Comparator<EvidenceReviewItemVO> title = Comparator.comparing(
                EvidenceReviewItemVO::getTitle,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
        );
        Comparator<EvidenceReviewItemVO> comparator = switch (sort == null ? "updated_desc" : sort) {
            case "updated_asc" -> updated;
            case "title_asc" -> title;
            case "title_desc" -> title.reversed();
            default -> updated.reversed();
        };
        return comparator.thenComparing(EvidenceReviewItemVO::getItemId, Comparator.reverseOrder());
    }

    private <T> void applyEvidenceStatus(
            LambdaQueryWrapper<T> wrapper,
            String requestedStatus,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> column
    ) {
        if (!StringUtils.hasText(requestedStatus)) return;
        if ("legacy_unverified".equals(requestedStatus)) {
            wrapper.and(value -> value.eq(column, requestedStatus).or().isNull(column));
        } else {
            wrapper.eq(column, requestedStatus);
        }
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    @Transactional
    public EvidenceReviewBatchResultVO reviewBatch(
            EvidenceReviewBatchUpdateDTO dto,
            AuthenticatedAdmin admin
    ) {
        List<EvidenceReviewBatchItemDTO> targets = new ArrayList<>(dto.getItems());
        Set<String> uniqueTargets = new HashSet<>();
        for (EvidenceReviewBatchItemDTO target : targets) {
            String targetKey = target.getItemType() + ":" + target.getItemId();
            if (!uniqueTargets.add(targetKey)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "批量审核不能包含重复资料");
            }
        }
        boolean downgrade = !VERIFIED.equals(dto.getEvidenceStatus());
        targets.sort(Comparator
                .comparing((EvidenceReviewBatchItemDTO target) -> downgrade
                        ? ("source".equals(target.getItemType()) ? 1 : 0)
                        : ("source".equals(target.getItemType()) ? 0 : 1))
                .thenComparing(EvidenceReviewBatchItemDTO::getItemType)
                .thenComparing(EvidenceReviewBatchItemDTO::getItemId));
        lockBatchSources(targets);

        String operationId = UUID.randomUUID().toString();
        List<EvidenceReviewItemVO> reviewedItems = new ArrayList<>(targets.size());
        for (EvidenceReviewBatchItemDTO target : targets) {
            EvidenceReviewUpdateDTO update = new EvidenceReviewUpdateDTO();
            update.setEvidenceStatus(dto.getEvidenceStatus());
            update.setExpectedEvidenceStatus(target.getExpectedEvidenceStatus());
            update.setExpectedUpdatedAt(target.getExpectedUpdatedAt());
            update.setExpectedVersion(target.getExpectedVersion());
            update.setReason(dto.getReason());
            update.setNotes(dto.getNotes());
            update.setCascade(dto.isCascade());
            reviewedItems.add(reviewInternal(
                    target.getItemType(),
                    target.getItemId(),
                    update,
                    admin,
                    operationId,
                    "batch_review"
            ));
        }

        EvidenceReviewBatchResultVO result = new EvidenceReviewBatchResultVO();
        result.setProcessedCount(reviewedItems.size());
        result.setItems(List.copyOf(reviewedItems));
        return result;
    }

    @Transactional
    public EvidenceReviewItemVO review(
            String itemType,
            Long itemId,
            EvidenceReviewUpdateDTO dto,
            AuthenticatedAdmin admin
    ) {
        return reviewInternal(
                itemType,
                itemId,
                dto,
                admin,
                UUID.randomUUID().toString(),
                "single_review"
        );
    }

    private EvidenceReviewItemVO reviewInternal(
            String itemType,
            Long itemId,
            EvidenceReviewUpdateDTO dto,
            AuthenticatedAdmin admin,
            String operationId,
            String actionType
    ) {
        return switch (itemType) {
            case "case" -> reviewCase(itemId, dto, admin, operationId, actionType);
            case "policy" -> reviewPolicy(itemId, dto, admin, operationId, actionType);
            case "source" -> reviewSource(itemId, dto, admin, operationId, actionType);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported evidence item type");
        };
    }

    private EvidenceReviewItemVO reviewCase(
            Long id,
            EvidenceReviewUpdateDTO dto,
            AuthenticatedAdmin admin,
            String operationId,
            String actionType
    ) {
        CaseItem snapshot = caseItemMapper.selectById(id);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case not found");
        }
        Source source = snapshot.getSourceId() == null ? null : sourceMapper.selectByIdForUpdate(snapshot.getSourceId());
        CaseItem item = caseItemMapper.selectByIdForUpdate(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Case not found");
        }
        requireExpectedState(item.getAiEvidenceStatus(), item.getEvidenceRevision(), item.getUpdatedAt(), dto);
        requireStatusChange(item.getAiEvidenceStatus(), dto.getEvidenceStatus());
        requireDecisionReason(dto.getEvidenceStatus(), dto.getReason());
        if (!java.util.Objects.equals(snapshot.getSourceId(), item.getSourceId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "资料来源已发生变化，请刷新后重试");
        }
        requireVerifiable(dto.getEvidenceStatus(), item.getStatus(), source);
        String previous = item.getAiEvidenceStatus();
        transitionCaseEvidence(item, dto.getEvidenceStatus());
        item.setAiEvidenceStatus(dto.getEvidenceStatus());
        audit("case", item.getId(), previous, dto.getEvidenceStatus(), dto.getReason(), dto.getNotes(),
                admin, operationId, actionType);
        CaseItem refreshed = caseItemMapper.selectById(item.getId());
        return caseItem(refreshed == null ? item : refreshed, source == null ? Map.of() : Map.of(source.getId(), source));
    }

    private EvidenceReviewItemVO reviewPolicy(
            Long id,
            EvidenceReviewUpdateDTO dto,
            AuthenticatedAdmin admin,
            String operationId,
            String actionType
    ) {
        Policy snapshot = policyMapper.selectById(id);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }
        Source source = snapshot.getSourceId() == null ? null : sourceMapper.selectByIdForUpdate(snapshot.getSourceId());
        Policy item = policyMapper.selectByIdForUpdate(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
        }
        requireExpectedState(item.getAiEvidenceStatus(), item.getEvidenceRevision(), item.getUpdatedAt(), dto);
        requireStatusChange(item.getAiEvidenceStatus(), dto.getEvidenceStatus());
        requireDecisionReason(dto.getEvidenceStatus(), dto.getReason());
        if (!java.util.Objects.equals(snapshot.getSourceId(), item.getSourceId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "资料来源已发生变化，请刷新后重试");
        }
        requireVerifiable(dto.getEvidenceStatus(), item.getStatus(), source);
        String previous = item.getAiEvidenceStatus();
        transitionPolicyEvidence(item, dto.getEvidenceStatus());
        item.setAiEvidenceStatus(dto.getEvidenceStatus());
        audit("policy", item.getId(), previous, dto.getEvidenceStatus(), dto.getReason(), dto.getNotes(),
                admin, operationId, actionType);
        Policy refreshed = policyMapper.selectById(item.getId());
        return policy(refreshed == null ? item : refreshed, source == null ? Map.of() : Map.of(source.getId(), source));
    }

    private EvidenceReviewItemVO reviewSource(
            Long id,
            EvidenceReviewUpdateDTO dto,
            AuthenticatedAdmin admin,
            String operationId,
            String actionType
    ) {
        Source item = sourceMapper.selectByIdForUpdate(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Source not found");
        }
        requireExpectedState(item.getAiEvidenceStatus(), item.getEvidenceRevision(), item.getUpdatedAt(), dto);
        requireStatusChange(item.getAiEvidenceStatus(), dto.getEvidenceStatus());
        requireDecisionReason(dto.getEvidenceStatus(), dto.getReason());
        if (VERIFIED.equals(item.getAiEvidenceStatus()) && !VERIFIED.equals(dto.getEvidenceStatus())) {
            int caseCount = verifiedCaseDependencyCount(item.getId());
            int policyCount = verifiedPolicyDependencyCount(item.getId());
            if ((caseCount + policyCount) > 0 && !dto.isCascade()) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "该来源仍被已核验案例或政策使用，请确认级联后再操作"
                );
            }
        }
        if (VERIFIED.equals(dto.getEvidenceStatus()) && !eligibleSourceForApproval(item)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "来源必须已发布且具有标题、发布机构和安全原文链接后才能核验");
        }
        String previous = item.getAiEvidenceStatus();
        transitionSourceEvidence(item, dto.getEvidenceStatus());
        item.setAiEvidenceStatus(dto.getEvidenceStatus());
        audit("source", item.getId(), previous, dto.getEvidenceStatus(), dto.getReason(), dto.getNotes(),
                admin, operationId, actionType);
        if (VERIFIED.equals(previous) && !VERIFIED.equals(dto.getEvidenceStatus()) && dto.isCascade()) {
            invalidateVerifiedDependencies(item.getId(), dto.getReason(), admin, operationId);
        }
        Source refreshed = sourceMapper.selectById(item.getId());
        return source(refreshed == null ? item : refreshed);
    }

    private void lockBatchSources(List<EvidenceReviewBatchItemDTO> targets) {
        Set<Long> sourceIds = new java.util.TreeSet<>();
        Map<String, Long> sourceSnapshots = new java.util.HashMap<>();
        for (EvidenceReviewBatchItemDTO target : targets) {
            switch (target.getItemType()) {
                case "source" -> {
                    sourceIds.add(target.getItemId());
                    sourceSnapshots.put(targetKey(target), target.getItemId());
                }
                case "case" -> {
                    CaseItem item = caseItemMapper.selectById(target.getItemId());
                    if (item != null && item.getSourceId() != null) {
                        sourceIds.add(item.getSourceId());
                        sourceSnapshots.put(targetKey(target), item.getSourceId());
                    }
                }
                case "policy" -> {
                    Policy item = policyMapper.selectById(target.getItemId());
                    if (item != null && item.getSourceId() != null) {
                        sourceIds.add(item.getSourceId());
                        sourceSnapshots.put(targetKey(target), item.getSourceId());
                    }
                }
                default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported evidence item type");
            }
        }
        for (Long sourceId : sourceIds) {
            sourceMapper.selectByIdForUpdate(sourceId);
        }
        for (EvidenceReviewBatchItemDTO target : targets) {
            if ("case".equals(target.getItemType())) {
                CaseItem item = caseItemMapper.selectById(target.getItemId());
                if (item == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Case not found");
                requireStableBatchSource(target, sourceSnapshots.get(targetKey(target)), item.getSourceId());
            } else if ("policy".equals(target.getItemType())) {
                Policy item = policyMapper.selectById(target.getItemId());
                if (item == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Policy not found");
                requireStableBatchSource(target, sourceSnapshots.get(targetKey(target)), item.getSourceId());
            }
        }
    }

    private String targetKey(EvidenceReviewBatchItemDTO target) {
        return target.getItemType() + ":" + target.getItemId();
    }

    private void requireStableBatchSource(EvidenceReviewBatchItemDTO target, Long expectedSourceId, Long actualSourceId) {
        if (!java.util.Objects.equals(expectedSourceId, actualSourceId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "批量审核期间资料来源已发生变化，请刷新后重试");
        }
    }

    private void transitionCaseEvidence(CaseItem item, String targetStatus) {
        long expectedVersion = revision(item.getEvidenceRevision());
        if (VERIFIED.equals(targetStatus)) {
            requireSingleAtomicUpdate(caseItemMapper.verifyEvidenceWithEligibleSource(
                    item.getId(), normalizeEvidenceStatus(item.getAiEvidenceStatus()), expectedVersion, item.getUpdatedAt()));
            item.setEvidenceRevision(expectedVersion + 1);
            return;
        }
        UpdateWrapper<CaseItem> wrapper = new UpdateWrapper<CaseItem>()
                .eq("id", item.getId())
                .eq("updated_at", item.getUpdatedAt())
                .eq("evidence_revision", expectedVersion)
                .set("ai_evidence_status", targetStatus)
                .set("evidence_revision", expectedVersion + 1);
        applyExpectedEvidenceCondition(wrapper, item.getAiEvidenceStatus());
        requireSingleAtomicUpdate(caseItemMapper.update(null, wrapper));
        item.setEvidenceRevision(expectedVersion + 1);
    }

    private void transitionPolicyEvidence(Policy item, String targetStatus) {
        long expectedVersion = revision(item.getEvidenceRevision());
        if (VERIFIED.equals(targetStatus)) {
            requireSingleAtomicUpdate(policyMapper.verifyEvidenceWithEligibleSource(
                    item.getId(), normalizeEvidenceStatus(item.getAiEvidenceStatus()), expectedVersion, item.getUpdatedAt()));
            item.setEvidenceRevision(expectedVersion + 1);
            return;
        }
        UpdateWrapper<Policy> wrapper = new UpdateWrapper<Policy>()
                .eq("id", item.getId())
                .eq("updated_at", item.getUpdatedAt())
                .eq("evidence_revision", expectedVersion)
                .set("ai_evidence_status", targetStatus)
                .set("evidence_revision", expectedVersion + 1);
        applyExpectedEvidenceCondition(wrapper, item.getAiEvidenceStatus());
        requireSingleAtomicUpdate(policyMapper.update(null, wrapper));
        item.setEvidenceRevision(expectedVersion + 1);
    }

    private void transitionSourceEvidence(Source item, String targetStatus) {
        long expectedVersion = revision(item.getEvidenceRevision());
        UpdateWrapper<Source> wrapper = new UpdateWrapper<Source>()
                .eq("id", item.getId())
                .eq("updated_at", item.getUpdatedAt())
                .eq("evidence_revision", expectedVersion)
                .set("ai_evidence_status", targetStatus)
                .set("evidence_revision", expectedVersion + 1);
        applyExpectedEvidenceCondition(wrapper, item.getAiEvidenceStatus());
        requireSingleAtomicUpdate(sourceMapper.update(null, wrapper));
        item.setEvidenceRevision(expectedVersion + 1);
    }

    private <T> void applyExpectedEvidenceCondition(UpdateWrapper<T> wrapper, String expectedStatus) {
        if (StringUtils.hasText(expectedStatus)) {
            wrapper.eq("ai_evidence_status", expectedStatus);
        } else {
            wrapper.isNull("ai_evidence_status");
        }
    }

    private void requireSingleAtomicUpdate(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "资料已被其他管理员修改，请刷新后重试");
        }
    }

    private void invalidateVerifiedDependencies(
            Long sourceId,
            String reason,
            AuthenticatedAdmin admin,
            String operationId
    ) {
        safe(caseItemMapper.selectList(new LambdaQueryWrapper<CaseItem>()
                .eq(CaseItem::getSourceId, sourceId)
                .eq(CaseItem::getAiEvidenceStatus, VERIFIED)))
                .forEach(item -> {
                    long expectedVersion = revision(item.getEvidenceRevision());
                    int affected = caseItemMapper.update(null, new UpdateWrapper<CaseItem>()
                            .eq("id", item.getId())
                            .eq("ai_evidence_status", VERIFIED)
                            .eq("evidence_revision", expectedVersion)
                            .set("ai_evidence_status", "legacy_unverified")
                            .set("evidence_revision", expectedVersion + 1));
                    requireSingleAtomicUpdate(affected);
                    item.setAiEvidenceStatus("legacy_unverified");
                    item.setEvidenceRevision(expectedVersion + 1);
                    audit("case", item.getId(), VERIFIED, "legacy_unverified", reason,
                            "关联来源失效，资料自动移回待审核", admin, operationId, "dependency_invalidated");
                });
        safe(policyMapper.selectList(new LambdaQueryWrapper<Policy>()
                .eq(Policy::getSourceId, sourceId)
                .eq(Policy::getAiEvidenceStatus, VERIFIED)))
                .forEach(item -> {
                    long expectedVersion = revision(item.getEvidenceRevision());
                    int affected = policyMapper.update(null, new UpdateWrapper<Policy>()
                            .eq("id", item.getId())
                            .eq("ai_evidence_status", VERIFIED)
                            .eq("evidence_revision", expectedVersion)
                            .set("ai_evidence_status", "legacy_unverified")
                            .set("evidence_revision", expectedVersion + 1));
                    requireSingleAtomicUpdate(affected);
                    item.setAiEvidenceStatus("legacy_unverified");
                    item.setEvidenceRevision(expectedVersion + 1);
                    audit("policy", item.getId(), VERIFIED, "legacy_unverified", reason,
                            "关联来源失效，资料自动移回待审核", admin, operationId, "dependency_invalidated");
                });
    }

    public void invalidateCaseAfterEvidenceEdit(CaseItem item, AuthenticatedAdmin admin) {
        if (item == null) return;
        if (!VERIFIED.equals(item.getAiEvidenceStatus())) {
            item.setEvidenceRevision(revision(item.getEvidenceRevision()) + 1);
            return;
        }
        String operationId = UUID.randomUUID().toString();
        transitionCaseEvidence(item, "legacy_unverified");
        item.setAiEvidenceStatus("legacy_unverified");
        audit("case", item.getId(), VERIFIED, "legacy_unverified", "evidence_relevant_content_changed",
                "已核验案例的证据相关字段发生修改，自动移回待审核", admin, operationId, "content_invalidated");
    }

    public void invalidatePolicyAfterEvidenceEdit(Policy item, AuthenticatedAdmin admin) {
        if (item == null) return;
        if (!VERIFIED.equals(item.getAiEvidenceStatus())) {
            item.setEvidenceRevision(revision(item.getEvidenceRevision()) + 1);
            return;
        }
        String operationId = UUID.randomUUID().toString();
        transitionPolicyEvidence(item, "legacy_unverified");
        item.setAiEvidenceStatus("legacy_unverified");
        audit("policy", item.getId(), VERIFIED, "legacy_unverified", "evidence_relevant_content_changed",
                "已核验政策的证据相关字段发生修改，自动移回待审核", admin, operationId, "content_invalidated");
    }

    public void invalidateSourceAfterEvidenceEdit(Source item, AuthenticatedAdmin admin) {
        if (item == null) return;
        if (!VERIFIED.equals(item.getAiEvidenceStatus())) {
            item.setEvidenceRevision(revision(item.getEvidenceRevision()) + 1);
            return;
        }
        String operationId = UUID.randomUUID().toString();
        transitionSourceEvidence(item, "legacy_unverified");
        item.setAiEvidenceStatus("legacy_unverified");
        audit("source", item.getId(), VERIFIED, "legacy_unverified", "evidence_relevant_content_changed",
                "已核验来源的关键字段发生修改，自动移回待审核", admin, operationId, "content_invalidated");
        invalidateVerifiedDependencies(item.getId(), "source_evidence_changed", admin, operationId);
    }

    public void requireSourceDeletionAllowed(Source item) {
        if (item == null) return;
        Source locked = sourceMapper.selectOne(new LambdaQueryWrapper<Source>()
                .eq(Source::getId, item.getId())
                .last("FOR UPDATE"));
        if (locked == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Source not found");
        item = locked;
        if (VERIFIED.equals(item.getAiEvidenceStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已核验来源必须先在证据审核工作台移回待审或排除后才能删除");
        }
        long caseCount = count(caseItemMapper.selectCount(new LambdaQueryWrapper<CaseItem>()
                .eq(CaseItem::getSourceId, item.getId())));
        long policyCount = count(policyMapper.selectCount(new LambdaQueryWrapper<Policy>()
                .eq(Policy::getSourceId, item.getId())));
        if (caseCount + policyCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该来源仍有关联案例或政策，不能直接删除");
        }
    }

    public void requireReviewedItemDeletionAllowed(String itemType, String evidenceStatus) {
        if (VERIFIED.equals(evidenceStatus)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "已核验" + ("case".equals(itemType) ? "案例" : "政策") + "必须先在证据审核工作台移回待审或排除后才能删除"
            );
        }
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

    private void requireDecisionReason(String requestedStatus, String reason) {
        if (!VERIFIED.equals(requestedStatus) && !StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "排除或移回待审必须填写原因");
        }
    }

    private void requireStatusChange(String currentStatus, String requestedStatus) {
        if (normalizeEvidenceStatus(currentStatus).equals(requestedStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资料已经处于目标审核状态");
        }
    }

    private void requireExpectedState(
            String actualStatus,
            Long actualVersion,
            LocalDateTime actualUpdatedAt,
            EvidenceReviewUpdateDTO dto
    ) {
        String normalizedActualStatus = StringUtils.hasText(actualStatus)
                ? actualStatus
                : "legacy_unverified";
        if (!normalizedActualStatus.equals(dto.getExpectedEvidenceStatus())
                || revision(actualVersion) != dto.getExpectedVersion()
                || !java.util.Objects.equals(actualUpdatedAt, dto.getExpectedUpdatedAt())) {
            throw new BusinessException(ErrorCode.CONFLICT, "资料已被其他管理员修改，请刷新后重试");
        }
    }

    private void audit(
            String itemType,
            Long itemId,
            String previousStatus,
            String newStatus,
            String reason,
            String notes,
            AuthenticatedAdmin admin,
            String operationId,
            String actionType
    ) {
        AiEvidenceReview review = new AiEvidenceReview();
        review.setItemType(itemType);
        review.setItemId(itemId);
        review.setPreviousStatus(previousStatus == null ? "legacy_unverified" : previousStatus);
        review.setNewStatus(newStatus);
        review.setAdminId(admin.adminId());
        review.setAdminUsername(admin.username());
        review.setActionType(actionType);
        review.setReason(StringUtils.hasText(reason) ? reason.trim() : null);
        review.setNotes(StringUtils.hasText(notes) ? notes.trim() : null);
        review.setOperationId(operationId);
        reviewMapper.insert(review);
    }

    private EvidenceReviewItemVO caseItem(CaseItem item, Map<Long, Source> sources) {
        Source source = item.getSourceId() == null ? null : sources.get(item.getSourceId());
        return item("case", item.getId(), item.getTitle(), item.getStatus(), item.getAiEvidenceStatus(), item.getSourceId(), source, item, item.getUpdatedAt());
    }

    private EvidenceReviewItemVO policy(Policy item, Map<Long, Source> sources) {
        Source source = item.getSourceId() == null ? null : sources.get(item.getSourceId());
        return item("policy", item.getId(), item.getTitle(), item.getStatus(), item.getAiEvidenceStatus(), item.getSourceId(), source, item, item.getUpdatedAt());
    }

    private EvidenceReviewItemVO source(Source source) {
        return item("source", source.getId(), source.getTitle(), source.getStatus(), source.getAiEvidenceStatus(), source.getId(), source, source, source.getUpdatedAt());
    }

    private EvidenceReviewItemVO item(
            String itemType,
            Long itemId,
            String title,
            String publicationStatus,
            String evidenceStatus,
            Long sourceId,
            Source source,
            Object content,
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
        ReviewEvaluation evaluation = evaluate(itemType, publicationStatus, source, content);
        result.setReviewable(evaluation.blockingReasons().isEmpty());
        result.setBlockingReasons(evaluation.blockingReasons());
        result.setVersion(content instanceof CaseItem caseItem ? revision(caseItem.getEvidenceRevision())
                : content instanceof Policy policy ? revision(policy.getEvidenceRevision())
                : content instanceof Source sourceItem ? revision(sourceItem.getEvidenceRevision()) : 0L);
        result.setUpdatedAt(updatedAt);
        return result;
    }

    private boolean eligibleSource(Source source) {
        return source != null
                && PUBLISHED.equals(source.getStatus())
                && VERIFIED.equals(source.getAiEvidenceStatus())
                && StringUtils.hasText(source.getTitle())
                && StringUtils.hasText(source.getPublisher())
                && isSafeEvidenceUrl(source.getUrl());
    }

    private boolean eligibleSourceForApproval(Source source) {
        return source != null
                && PUBLISHED.equals(source.getStatus())
                && StringUtils.hasText(source.getTitle())
                && StringUtils.hasText(source.getPublisher())
                && isSafeEvidenceUrl(source.getUrl());
    }

    private boolean isSafeEvidenceUrl(String value) {
        return EvidenceUrlPolicy.isSafe(value);
    }
}
