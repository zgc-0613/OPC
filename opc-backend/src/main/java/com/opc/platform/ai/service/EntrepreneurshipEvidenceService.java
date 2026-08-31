package com.opc.platform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.EntrepreneurshipAdviceRequestDTO;
import com.opc.platform.ai.dto.EntrepreneurshipReadinessRequestDTO;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.vo.EntrepreneurshipReadinessVO;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.casetag.entity.CaseTag;
import com.opc.platform.casetag.mapper.CaseTagMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policyindustrytag.entity.PolicyIndustryTag;
import com.opc.platform.policyindustrytag.mapper.PolicyIndustryTagMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.tag.service.IndustryTagService;
import com.opc.platform.tag.vo.IndustryResolution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
public class EntrepreneurshipEvidenceService {

    private static final String PUBLISHED = "published";
    private static final String VERIFIED = "verified";
    private static final int CASE_LIMIT = 6;
    private static final int POLICY_LIMIT = 6;
    private static final Duration REGION_CACHE_TTL = Duration.ofSeconds(30);

    private final CaseItemMapper caseItemMapper;
    private final PolicyMapper policyMapper;
    private final SourceMapper sourceMapper;
    private final RegionMapper regionMapper;
    private final CaseTagMapper caseTagMapper;
    private final PolicyIndustryTagMapper policyIndustryTagMapper;
    private final IndustryTagService industryTagService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private volatile RegionCache regionCache = new RegionCache(Map.of(), Instant.EPOCH);

    public Assessment assess(EntrepreneurshipAdviceRequestDTO request, boolean allowAiResolution) {
        return assess(
                request.getRegionId(),
                request.getIndustryTagId(),
                request.getIndustry(),
                request.getGoal(),
                allowAiResolution
        );
    }

    public Assessment assess(EntrepreneurshipReadinessRequestDTO request, boolean allowAiResolution) {
        return assess(
                request.getRegionId(),
                request.getIndustryTagId(),
                request.getIndustry(),
                null,
                allowAiResolution
        );
    }

    public EntrepreneurshipReadinessVO readiness(
            EntrepreneurshipReadinessRequestDTO request,
            boolean allowAiResolution
    ) {
        return toReadiness(assess(request, allowAiResolution));
    }

    public void requireUnchanged(Assessment assessment) {
        List<ScoredCase> currentCases = assessment.cases().stream()
                .map(item -> currentCase(item))
                .sorted(Comparator.comparing(item -> item.item().getId()))
                .toList();
        List<ScoredPolicy> currentPolicies = assessment.policies().stream()
                .map(item -> currentPolicy(item))
                .sorted(Comparator.comparing(item -> item.item().getId()))
                .toList();
        Map<Long, Source> currentSources = assessment.sources().keySet().stream()
                .sorted()
                .map(sourceMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Source::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        Map<Long, Set<Long>> currentPolicyIndustryTags = policyIndustryTagsByPolicyIds(
                currentPolicies.stream().map(item -> item.item().getId()).toList()
        );

        if (currentCases.size() != assessment.cases().size()
                || currentPolicies.size() != assessment.policies().size()
                || currentSources.size() != assessment.sources().size()
                || currentCases.stream().anyMatch(item -> !eligible(item.item()))
                || currentPolicies.stream().anyMatch(item -> !eligible(item.item()))
                || currentSources.values().stream().anyMatch(source -> !eligible(source))) {
            throw new BusinessException(ErrorCode.CONFLICT, "分析期间证据已变更，请重新生成");
        }
        if (!Objects.equals(assessment.hash(), evidenceHash(
                assessment.industry(), currentCases, currentPolicies, currentSources, currentPolicyIndustryTags))) {
            throw new BusinessException(ErrorCode.CONFLICT, "分析期间证据版本已变更，请重新生成");
        }
    }

    private ScoredCase currentCase(ScoredCase original) {
        CaseItem current = caseItemMapper.selectById(original.item().getId());
        if (current == null || !sameVersion(original.item(), current)) {
            throw new BusinessException(ErrorCode.CONFLICT, "分析期间案例证据已变更，请重新生成");
        }
        return new ScoredCase(current, original.relevance(), original.geographicRank(),
                original.geographicLevel(), original.matchReason(), original.regionName());
    }

    private ScoredPolicy currentPolicy(ScoredPolicy original) {
        Policy current = policyMapper.selectById(original.item().getId());
        if (current == null || !sameVersion(original.item(), current)) {
            throw new BusinessException(ErrorCode.CONFLICT, "分析期间政策证据已变更，请重新生成");
        }
        return new ScoredPolicy(current, original.relevance(), original.geographicRank(),
                original.geographicLevel(), original.matchReason(), original.regionName());
    }

    private boolean sameVersion(CaseItem expected, CaseItem actual) {
        return Objects.equals(expected.getStatus(), actual.getStatus())
                && Objects.equals(expected.getAiEvidenceStatus(), actual.getAiEvidenceStatus())
                && Objects.equals(expected.getSourceId(), actual.getSourceId())
                && Objects.equals(revision(expected.getEvidenceRevision()), revision(actual.getEvidenceRevision()))
                && Objects.equals(expected.getUpdatedAt(), actual.getUpdatedAt());
    }

    private boolean sameVersion(Policy expected, Policy actual) {
        return Objects.equals(expected.getStatus(), actual.getStatus())
                && Objects.equals(expected.getAiEvidenceStatus(), actual.getAiEvidenceStatus())
                && Objects.equals(expected.getApplicabilityMode(), actual.getApplicabilityMode())
                && Objects.equals(expected.getSourceId(), actual.getSourceId())
                && Objects.equals(revision(expected.getEvidenceRevision()), revision(actual.getEvidenceRevision()))
                && Objects.equals(expected.getUpdatedAt(), actual.getUpdatedAt());
    }

    private boolean sameVersion(Source expected, Source actual) {
        return Objects.equals(expected.getStatus(), actual.getStatus())
                && Objects.equals(expected.getAiEvidenceStatus(), actual.getAiEvidenceStatus())
                && Objects.equals(revision(expected.getEvidenceRevision()), revision(actual.getEvidenceRevision()))
                && Objects.equals(expected.getUpdatedAt(), actual.getUpdatedAt());
    }

    private Assessment assess(
            Long regionId,
            Long industryTagId,
            String industryText,
            String goal,
            boolean allowAiResolution
    ) {
        Region requestedRegion = regionMapper.selectById(regionId);
        if (requestedRegion == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所选地区不存在");
        }
        IndustryResolution resolution = industryTagService.resolve(industryTagId, industryText, allowAiResolution);
        if (resolution.tagId() == null) {
            return emptyAssessment(requestedRegion, resolution, List.of("无相关行业"));
        }

        Map<Long, Region> regions = regionMap(requestedRegion);
        Set<Long> relatedTagIds = new LinkedHashSet<>(industryTagService.relatedTagIds(resolution.tagId()));
        relatedTagIds.add(resolution.tagId());
        Map<Long, Set<Long>> caseTags = caseTagsByItem(relatedTagIds);
        Map<Long, Set<Long>> policyTags = policyIndustryTagsByItem(relatedTagIds);
        String requestedText = StringUtils.hasText(industryText) ? industryText.trim() : resolution.name();

        LambdaQueryWrapper<CaseItem> caseQuery = new LambdaQueryWrapper<CaseItem>()
                .eq(CaseItem::getStatus, PUBLISHED)
                .eq(CaseItem::getAiEvidenceStatus, VERIFIED);
        applyCaseIndustryFilter(caseQuery, caseTags.keySet(), industryTerms(requestedText, resolution.name()));

        List<ScoredCase> relevantCases = safe(caseItemMapper.selectList(
                caseQuery
        )).stream()
                .map(item -> scoreCase(item, resolution, requestedText, goal, relatedTagIds, caseTags, regions, regionId))
                .filter(item -> item.relevance() > 0)
                .sorted(caseComparator())
                .toList();

        Set<Long> eligiblePolicyRegionIds = regions.values().stream()
                .filter(region -> !"cross_region".equals(geography(
                        region.getId(), regions, regionId
                ).level()))
                .map(Region::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LambdaQueryWrapper<Policy> policyQuery = new LambdaQueryWrapper<Policy>()
                .eq(Policy::getStatus, PUBLISHED)
                .eq(Policy::getAiEvidenceStatus, VERIFIED)
                .in(Policy::getRegionId, eligiblePolicyRegionIds);
        applyPolicyApplicabilityFilter(policyQuery, policyTags.keySet());

        List<Policy> verifiedPolicyCandidates = safe(policyMapper.selectList(policyQuery));
        int verifiedPolicyCandidateCount = verifiedPolicyCandidates.size();

        Set<Long> candidateSourceIds = new LinkedHashSet<>();
        relevantCases.stream().map(item -> item.item().getSourceId()).filter(Objects::nonNull).forEach(candidateSourceIds::add);
        verifiedPolicyCandidates.stream().map(Policy::getSourceId).filter(Objects::nonNull).forEach(candidateSourceIds::add);
        Map<Long, Source> candidateSources = candidateSourceIds.isEmpty()
                ? Map.of()
                : safe(sourceMapper.selectBatchIds(candidateSourceIds)).stream()
                        .filter(this::eligible)
                        .collect(Collectors.toMap(Source::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, Source> sources = new LinkedHashMap<>();
        int sourceRejected = 0;
        List<ScoredCase> cases = new ArrayList<>();
        for (ScoredCase candidate : relevantCases) {
            Source source = candidateSources.get(candidate.item().getSourceId());
            if (source != null) {
                if (cases.size() < CASE_LIMIT) {
                    cases.add(candidate);
                    sources.put(source.getId(), source);
                }
            } else {
                sourceRejected++;
            }
        }
        int sourceRejectedPolicyCount = 0;
        int regionMatchedPolicyCount = 0;
        int directIndustryPolicyCount = 0;
        int generalPolicyCount = 0;
        int unclassifiedPolicyCount = 0;
        List<ScoredPolicy> selectablePolicies = new ArrayList<>();
        for (Policy candidate : verifiedPolicyCandidates) {
            Source source = candidateSources.get(candidate.getSourceId());
            if (source == null) {
                sourceRejectedPolicyCount++;
                continue;
            }
            ScoredPolicy scored = scorePolicy(
                    candidate, resolution, requestedText, goal, relatedTagIds, policyTags, regions, regionId
            );
            if ("cross_region".equals(scored.geographicLevel())) {
                continue;
            }
            regionMatchedPolicyCount++;
            switch (safe(candidate.getApplicabilityMode())) {
                case "specific" -> {
                    if (scored.relevance() > 0) {
                        directIndustryPolicyCount++;
                        selectablePolicies.add(scored);
                    }
                }
                case "general" -> {
                    generalPolicyCount++;
                    selectablePolicies.add(scored);
                }
                default -> {
                    unclassifiedPolicyCount++;
                    selectablePolicies.add(scored);
                }
            }
        }
        selectablePolicies.sort(policyComparator());
        List<ScoredPolicy> policies = new ArrayList<>();
        for (ScoredPolicy candidate : selectablePolicies) {
            if (policies.size() >= POLICY_LIMIT) {
                break;
            }
            policies.add(candidate);
            Source source = candidateSources.get(candidate.item().getSourceId());
            sources.put(source.getId(), source);
        }
        sourceRejected += sourceRejectedPolicyCount;
        Map<Long, Set<Long>> selectedPolicyIndustryTags = policyIndustryTagsByPolicyIds(
                policies.stream().map(item -> item.item().getId()).toList()
        );
        PolicyDiagnostics policyDiagnostics = new PolicyDiagnostics(
                verifiedPolicyCandidateCount,
                regionMatchedPolicyCount,
                directIndustryPolicyCount,
                generalPolicyCount,
                unclassifiedPolicyCount,
                sourceRejectedPolicyCount,
                policies.size()
        );

        List<String> reasons = new ArrayList<>();
        if (resolution.requiresConfirmation()) {
            reasons.add("行业标签匹配置信度较低，请确认行业");
        }
        if (cases.isEmpty()) {
            reasons.add("无已核验案例");
        }
        if (verifiedPolicyCandidateCount == 0) {
            reasons.add("没有已核验政策");
        } else {
            if (directIndustryPolicyCount == 0) {
                reasons.add("当前行业暂无直接匹配政策");
            }
            if (generalPolicyCount > 0) {
                reasons.add("当前地区有通用创业政策可参考");
            }
            if (unclassifiedPolicyCount > 0) {
                reasons.add("存在 " + unclassifiedPolicyCount
                        + " 条尚未完成行业适用性分类的政策，将仅作为地区政策参考");
            }
        }
        if (sourceRejected > 0) {
            reasons.add("存在 " + sourceRejected + " 条相关资料的来源未核验");
        }
        int selectedEvidenceCount = cases.size() + policies.size();
        String readinessStatus;
        if (resolution.requiresConfirmation() || selectedEvidenceCount == 0) {
            readinessStatus = "insufficient";
        } else if (!cases.isEmpty() && !policies.isEmpty() && selectedEvidenceCount >= 3) {
            readinessStatus = "sufficient";
        } else {
            readinessStatus = "partial";
            reasons.add("证据有限：将限制结论范围，不补造缺失事实");
        }
        boolean evidenceAvailable = !"insufficient".equals(readinessStatus);
        String hash = evidenceHash(resolution, cases, policies, sources, selectedPolicyIndustryTags);
        return new Assessment(
                requestedRegion,
                resolution,
                List.copyOf(cases),
                List.copyOf(policies),
                Map.copyOf(sources),
                copyRelations(selectedPolicyIndustryTags),
                List.copyOf(reasons),
                readinessStatus,
                relevantCases.size() + selectablePolicies.size(),
                policyDiagnostics,
                evidenceAvailable,
                aiClient.descriptor().available(),
                hash
        );
    }

    private Assessment emptyAssessment(Region region, IndustryResolution resolution, List<String> reasons) {
        return new Assessment(
                region, resolution, List.of(), List.of(), Map.of(), Map.of(), reasons,
                "insufficient", 0, PolicyDiagnostics.empty(), false,
                aiClient.descriptor().available(), sha256(region.getId() + ":unresolved")
        );
    }

    private EntrepreneurshipReadinessVO toReadiness(Assessment assessment) {
        EntrepreneurshipReadinessVO result = new EntrepreneurshipReadinessVO();
        result.setModelAvailable(assessment.modelAvailable());
        result.setEvidenceAvailable(assessment.evidenceAvailable());
        result.setReadinessStatus(assessment.readinessStatus());
        result.setResolvedIndustryTag(assessment.industry());
        result.setMatchMethod(assessment.industry().method());
        result.setConfidence(assessment.industry().confidence());
        result.setVerifiedCaseCount(assessment.cases().size());
        result.setVerifiedPolicyCount(assessment.policies().size());
        result.setVerifiedPolicyCandidateCount(assessment.policyDiagnostics().verifiedPolicyCandidateCount());
        result.setRegionMatchedPolicyCount(assessment.policyDiagnostics().regionMatchedPolicyCount());
        result.setDirectIndustryPolicyCount(assessment.policyDiagnostics().directIndustryPolicyCount());
        result.setGeneralPolicyCount(assessment.policyDiagnostics().generalPolicyCount());
        result.setUnclassifiedPolicyCount(assessment.policyDiagnostics().unclassifiedPolicyCount());
        result.setSourceRejectedPolicyCount(assessment.policyDiagnostics().sourceRejectedPolicyCount());
        result.setSelectedPolicyCount(assessment.policyDiagnostics().selectedPolicyCount());
        result.setVerifiedSourceCount(assessment.sources().size());
        result.setTotalRelevantCount(assessment.totalRelevantCount());
        result.setSelectedEvidenceCount(assessment.cases().size() + assessment.policies().size());
        result.setDirectRegionCount(
                countLevel(assessment, "exact_region") + countLevel(assessment, "within_region")
        );
        result.setBroaderRegionCount(
                countLevel(assessment, "broader_region")
                        + countLevel(assessment, "national")
                        + countLevel(assessment, "unknown")
        );
        result.setExactRegionCount(countLevel(assessment, "exact_region"));
        result.setParentRegionCount(countLevel(assessment, "broader_region"));
        result.setNationalCount(countLevel(assessment, "national"));
        result.setCrossRegionCount(countLevel(assessment, "cross_region"));
        result.setReasons(assessment.reasons());
        return result;
    }

    private int countLevel(Assessment assessment, String level) {
        long cases = assessment.cases().stream().filter(item -> level.equals(item.geographicLevel())).count();
        long policies = assessment.policies().stream().filter(item -> level.equals(item.geographicLevel())).count();
        return Math.toIntExact(cases + policies);
    }

    private ScoredCase scoreCase(
            CaseItem item,
            IndustryResolution resolution,
            String industryText,
            String goal,
            Set<Long> relatedTagIds,
            Map<Long, Set<Long>> relations,
            Map<Long, Region> regions,
            Long requestedRegionId
    ) {
        int relevance = relevance(
                searchable(item.getTitle(), item.getArticleTitle(), item.getCategory(), item.getSubcategory(),
                        item.getSummary(), item.getTags(), item.getBusinessModel(), item.getOutcome()),
                industryText,
                resolution.name(),
                goal,
                relations.getOrDefault(item.getId(), Set.of()),
                relatedTagIds
        );
        Geographic geographic = geography(item.getRegionId(), regions, requestedRegionId);
        return new ScoredCase(item, relevance, geographic.rank(), geographic.level(), geographic.reason(), geographic.regionName());
    }

    private ScoredPolicy scorePolicy(
            Policy item,
            IndustryResolution resolution,
            String industryText,
            String goal,
            Set<Long> relatedTagIds,
            Map<Long, Set<Long>> relations,
            Map<Long, Region> regions,
            Long requestedRegionId
    ) {
        Set<Long> explicitIndustryTags = relations.getOrDefault(item.getId(), Set.of());
        boolean directIndustry = explicitIndustryTags.stream().anyMatch(relatedTagIds::contains);
        int textRelevance = textScore(
                searchable(item.getTitle(), item.getPolicyType(), item.getSummary(), item.getTags(),
                        item.getKeyPoints(), item.getSupportMeasures()),
                industryText,
                20
        );
        if (!Objects.equals(industryText, resolution.name())) {
            textRelevance += textScore(searchable(item.getTitle(), item.getSummary(), item.getKeyPoints()), resolution.name(), 20);
        }
        textRelevance += textScore(searchable(item.getTitle(), item.getSummary(), item.getSupportMeasures()), goal, 2);
        int relevance = switch (safe(item.getApplicabilityMode())) {
            case "specific" -> directIndustry ? 100 + textRelevance : 0;
            case "general" -> 20 + textRelevance;
            default -> 5 + textRelevance;
        };
        Geographic geographic = geography(item.getRegionId(), regions, requestedRegionId);
        String applicabilityReason = directIndustry
                ? "指定行业政策"
                : "general".equals(item.getApplicabilityMode()) ? "通用创业政策" : "行业适用性未分类";
        return new ScoredPolicy(item, relevance, geographic.rank(), geographic.level(),
                applicabilityReason + " · " + geographic.reason(), geographic.regionName());
    }

    private int relevance(
            String searchable,
            String requestedText,
            String canonicalName,
            String goal,
            Set<Long> itemTags,
            Set<Long> relatedTagIds
    ) {
        int industryScore = itemTags.stream().anyMatch(relatedTagIds::contains) ? 50 : 0;
        industryScore += textScore(searchable, requestedText, 20);
        if (!Objects.equals(requestedText, canonicalName)) {
            industryScore += textScore(searchable, canonicalName, 20);
        }
        if (industryScore <= 0) {
            return 0;
        }
        return industryScore + textScore(searchable, goal, 2);
    }

    private int textScore(String searchable, String value, int weight) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        String normalized = value.trim().toLowerCase();
        int score = searchable.contains(normalized) ? weight : 0;
        for (String term : normalized.split("[\\s,，、/]+")) {
            if (term.length() >= 2 && searchable.contains(term)) {
                score += Math.max(1, weight / 5);
            }
        }
        return score;
    }

    private Geographic geography(
            Long itemRegionId,
            Map<Long, Region> regions,
            Long requestedRegionId
    ) {
        Region itemRegion = itemRegionId == null ? null : regions.get(itemRegionId);
        String name = itemRegion == null ? "未标注地区" : itemRegion.getName();
        if (Objects.equals(itemRegionId, requestedRegionId)) {
            return new Geographic(5, "exact_region", "当前地区直接匹配", name);
        }
        if (itemRegion == null) {
            return new Geographic(2, "unknown", "未标注地区的通用参考资料", name);
        }
        if (isAncestor(requestedRegionId, itemRegionId, regions)) {
            return new Geographic(4, "within_region", "当前地区范围内的下级地区资料", name);
        }
        if (isAncestor(itemRegionId, requestedRegionId, regions)) {
            if ("country".equalsIgnoreCase(itemRegion.getLevel())) {
                return new Geographic(2, "national", "国家级通用资料", name);
            }
            return new Geographic(3, "broader_region", "上级地区背景资料", name);
        }
        if ("country".equalsIgnoreCase(itemRegion.getLevel())) {
            return new Geographic(2, "national", "国家级通用资料", name);
        }
        return new Geographic(1, "cross_region", "跨地区借鉴案例，非本地案例", name);
    }

    private Map<Long, Region> regionMap(Region requested) {
        Instant now = Instant.now();
        RegionCache current = regionCache;
        if (!current.expiresAt().isAfter(now)) {
            synchronized (this) {
                current = regionCache;
                if (!current.expiresAt().isAfter(now)) {
                    Map<Long, Region> refreshed = safe(regionMapper.selectList(new LambdaQueryWrapper<Region>())).stream()
                            .filter(region -> region.getId() != null)
                            .collect(Collectors.toMap(
                                    Region::getId, Function.identity(), (left, right) -> left, HashMap::new
                            ));
                    current = new RegionCache(Map.copyOf(refreshed), now.plus(REGION_CACHE_TTL));
                    regionCache = current;
                }
            }
        }
        if (current.regions().containsKey(requested.getId())) {
            return current.regions();
        }
        Map<Long, Region> withRequested = new HashMap<>(current.regions());
        withRequested.put(requested.getId(), requested);
        return Map.copyOf(withRequested);
    }

    private boolean isAncestor(Long ancestorId, Long descendantId, Map<Long, Region> regions) {
        if (ancestorId == null || descendantId == null) {
            return false;
        }
        Region current = regions.get(descendantId);
        Set<Long> visited = new LinkedHashSet<>();
        while (current != null && current.getId() != null && visited.add(current.getId())) {
            if (Objects.equals(current.getId(), ancestorId)) {
                return true;
            }
            current = current.getParentId() == null ? null : regions.get(current.getParentId());
        }
        return false;
    }

    private Map<Long, Set<Long>> caseTagsByItem(Set<Long> relatedTagIds) {
        return safe(caseTagMapper.selectList(
                new LambdaQueryWrapper<CaseTag>().in(CaseTag::getTagId, relatedTagIds)
        )).stream()
                .collect(Collectors.groupingBy(
                        CaseTag::getCaseId,
                        Collectors.mapping(CaseTag::getTagId, Collectors.toSet())
                ));
    }

    private Map<Long, Set<Long>> policyIndustryTagsByItem(Set<Long> relatedTagIds) {
        return safe(policyIndustryTagMapper.selectList(
                new LambdaQueryWrapper<PolicyIndustryTag>().in(PolicyIndustryTag::getIndustryTagId, relatedTagIds)
        )).stream()
                .collect(Collectors.groupingBy(
                        PolicyIndustryTag::getPolicyId,
                        Collectors.mapping(PolicyIndustryTag::getIndustryTagId, Collectors.toSet())
                ));
    }

    private Map<Long, Set<Long>> policyIndustryTagsByPolicyIds(Collection<Long> policyIds) {
        if (policyIds == null || policyIds.isEmpty()) {
            return Map.of();
        }
        return safe(policyIndustryTagMapper.selectList(
                new LambdaQueryWrapper<PolicyIndustryTag>().in(PolicyIndustryTag::getPolicyId, policyIds)
        )).stream()
                .collect(Collectors.groupingBy(
                        PolicyIndustryTag::getPolicyId,
                        Collectors.mapping(PolicyIndustryTag::getIndustryTagId, Collectors.toCollection(TreeSet::new))
                ));
    }

    private Map<Long, Set<Long>> copyRelations(Map<Long, Set<Long>> relations) {
        return relations.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Set.copyOf(entry.getValue())
        ));
    }

    private Set<String> industryTerms(String requestedText, String canonicalName) {
        Set<String> terms = new LinkedHashSet<>();
        for (String value : List.of(safe(requestedText), safe(canonicalName))) {
            if (value.length() >= 2) terms.add(value);
            for (String term : value.split("[\\s,，、/]+")) {
                if (term.length() >= 2) terms.add(term);
            }
        }
        return terms;
    }

    private void applyCaseIndustryFilter(
            LambdaQueryWrapper<CaseItem> wrapper,
            Set<Long> taggedItemIds,
            Set<String> terms
    ) {
        wrapper.and(group -> {
            boolean hasPrevious = false;
            if (!taggedItemIds.isEmpty()) {
                group.in(CaseItem::getId, taggedItemIds);
                hasPrevious = true;
            }
            for (String term : terms) {
                if (hasPrevious) group.or();
                group.and(text -> text.like(CaseItem::getTitle, term)
                        .or().like(CaseItem::getArticleTitle, term)
                        .or().like(CaseItem::getCategory, term)
                        .or().like(CaseItem::getSubcategory, term)
                        .or().like(CaseItem::getSummary, term)
                        .or().like(CaseItem::getTags, term)
                        .or().like(CaseItem::getBusinessModel, term)
                        .or().like(CaseItem::getOutcome, term));
                hasPrevious = true;
            }
        });
    }

    private void applyPolicyApplicabilityFilter(
            LambdaQueryWrapper<Policy> wrapper,
            Set<Long> directlyRelatedPolicyIds
    ) {
        wrapper.and(scope -> {
            scope.eq(Policy::getApplicabilityMode, "general")
                    .or()
                    .eq(Policy::getApplicabilityMode, "unclassified");
            if (!directlyRelatedPolicyIds.isEmpty()) {
                scope.or(branch -> branch
                        .eq(Policy::getApplicabilityMode, "specific")
                        .in(Policy::getId, directlyRelatedPolicyIds));
            }
        });
    }

    private boolean registerEligibleSource(Long sourceId, Map<Long, Source> sources) {
        if (sourceId == null) {
            return false;
        }
        Source source = sources.get(sourceId);
        if (source == null) {
            source = sourceMapper.selectById(sourceId);
            if (eligible(source)) {
                sources.put(sourceId, source);
            }
        }
        return sources.containsKey(sourceId);
    }

    private boolean eligible(Source source) {
        return source != null
                && PUBLISHED.equals(source.getStatus())
                && VERIFIED.equals(source.getAiEvidenceStatus())
                && StringUtils.hasText(source.getTitle())
                && StringUtils.hasText(source.getPublisher())
                && StringUtils.hasText(source.getUrl());
    }

    private boolean eligible(CaseItem item) {
        return item != null && PUBLISHED.equals(item.getStatus()) && VERIFIED.equals(item.getAiEvidenceStatus());
    }

    private boolean eligible(Policy item) {
        return item != null && PUBLISHED.equals(item.getStatus()) && VERIFIED.equals(item.getAiEvidenceStatus());
    }

    private Comparator<ScoredCase> caseComparator() {
        return Comparator.comparingInt(ScoredCase::relevance).reversed()
                .thenComparing(Comparator.comparingInt(ScoredCase::geographicRank).reversed())
                .thenComparing(item -> date(item.item().getAccessedAt()), Comparator.reverseOrder())
                .thenComparing(item -> item.item().getId(), Comparator.reverseOrder());
    }

    private Comparator<ScoredPolicy> policyComparator() {
        return Comparator.comparingInt(ScoredPolicy::geographicRank).reversed()
                .thenComparing(Comparator.comparingInt(this::policyApplicabilityRank).reversed())
                .thenComparing(Comparator.comparingInt(ScoredPolicy::relevance).reversed())
                .thenComparing(item -> date(item.item().getPublishDate()), Comparator.reverseOrder())
                .thenComparing(item -> item.item().getId(), Comparator.reverseOrder());
    }

    private int policyApplicabilityRank(ScoredPolicy policy) {
        return switch (safe(policy.item().getApplicabilityMode())) {
            case "specific" -> 3;
            case "general" -> 2;
            default -> 1;
        };
    }

    private LocalDate date(LocalDate value) {
        return value == null ? LocalDate.MIN : value;
    }

    private String evidenceHash(
            IndustryResolution industry,
            List<ScoredCase> cases,
            List<ScoredPolicy> policies,
            Map<Long, Source> sources,
            Map<Long, Set<Long>> policyIndustryTags
    ) {
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("industry", industry);
            content.put("cases", cases.stream().sorted(Comparator.comparing(item -> item.item().getId())).map(item -> List.of(
                    item.item().getId(), safe(item.item().getTitle()), safe(item.item().getSummary()),
                    safe(item.item().getBusinessModel()), safe(item.item().getOutcome()),
                    safe(item.item().getStatus()), safe(item.item().getAiEvidenceStatus()),
                    revision(item.item().getEvidenceRevision()),
                    item.item().getSourceId(),
                    safe(item.item().getUpdatedAt() == null ? null : item.item().getUpdatedAt().toString())
            )).toList());
            content.put("policies", policies.stream().sorted(Comparator.comparing(item -> item.item().getId())).map(item -> List.of(
                    item.item().getId(), safe(item.item().getTitle()), safe(item.item().getSummary()),
                    safe(item.item().getKeyPoints()), safe(item.item().getSupportMeasures()),
                    safe(item.item().getStatus()), safe(item.item().getAiEvidenceStatus()),
                    safe(item.item().getApplicabilityMode()),
                    policyIndustryTags.getOrDefault(item.item().getId(), Set.of()).stream().sorted().toList(),
                    revision(item.item().getEvidenceRevision()),
                    item.item().getSourceId(),
                    safe(item.item().getUpdatedAt() == null ? null : item.item().getUpdatedAt().toString())
            )).toList());
            content.put("sources", sources.values().stream().sorted(Comparator.comparing(Source::getId)).map(source -> List.of(
                    source.getId(), safe(source.getTitle()), safe(source.getPublisher()), safe(source.getUrl()), safe(source.getNotes()),
                    safe(source.getStatus()), safe(source.getAiEvidenceStatus()), revision(source.getEvidenceRevision()),
                    safe(source.getUpdatedAt() == null ? null : source.getUpdatedAt().toString())
            )).toList());
            return sha256(objectMapper.writeValueAsString(content));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "证据版本无法计算");
        }
    }

    private String searchable(String... values) {
        return java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.joining(" "));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Evidence hash failed");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private long revision(Long value) {
        return value == null ? 0L : value;
    }

    private <T> List<T> safe(Collection<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Assessment(
            Region region,
            IndustryResolution industry,
            List<ScoredCase> cases,
            List<ScoredPolicy> policies,
            Map<Long, Source> sources,
            Map<Long, Set<Long>> policyIndustryTags,
            List<String> reasons,
            String readinessStatus,
            int totalRelevantCount,
            PolicyDiagnostics policyDiagnostics,
            boolean evidenceAvailable,
            boolean modelAvailable,
            String hash
    ) {
    }

    public record PolicyDiagnostics(
            int verifiedPolicyCandidateCount,
            int regionMatchedPolicyCount,
            int directIndustryPolicyCount,
            int generalPolicyCount,
            int unclassifiedPolicyCount,
            int sourceRejectedPolicyCount,
            int selectedPolicyCount
    ) {
        private static PolicyDiagnostics empty() {
            return new PolicyDiagnostics(0, 0, 0, 0, 0, 0, 0);
        }
    }

    public record ScoredCase(
            CaseItem item,
            int relevance,
            int geographicRank,
            String geographicLevel,
            String matchReason,
            String regionName
    ) {
    }

    public record ScoredPolicy(
            Policy item,
            int relevance,
            int geographicRank,
            String geographicLevel,
            String matchReason,
            String regionName
    ) {
    }

    private record Geographic(int rank, String level, String reason, String regionName) {
    }

    private record RegionCache(Map<Long, Region> regions, Instant expiresAt) {
    }
}
