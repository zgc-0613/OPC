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
import com.opc.platform.policytag.entity.PolicyTag;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntrepreneurshipEvidenceService {

    private static final String PUBLISHED = "published";
    private static final String VERIFIED = "verified";
    private static final int CASE_LIMIT = 6;
    private static final int POLICY_LIMIT = 6;

    private final CaseItemMapper caseItemMapper;
    private final PolicyMapper policyMapper;
    private final SourceMapper sourceMapper;
    private final RegionMapper regionMapper;
    private final CaseTagMapper caseTagMapper;
    private final PolicyTagMapper policyTagMapper;
    private final IndustryTagService industryTagService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

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
        Long provinceId = provinceId(requestedRegion, regions);
        Set<Long> relatedTagIds = new LinkedHashSet<>(industryTagService.relatedTagIds(resolution.tagId()));
        relatedTagIds.add(resolution.tagId());
        Map<Long, Set<Long>> caseTags = caseTagsByItem();
        Map<Long, Set<Long>> policyTags = policyTagsByItem();
        String requestedText = StringUtils.hasText(industryText) ? industryText.trim() : resolution.name();

        List<ScoredCase> relevantCases = safe(caseItemMapper.selectList(
                new LambdaQueryWrapper<CaseItem>()
                        .eq(CaseItem::getStatus, PUBLISHED)
                        .eq(CaseItem::getAiEvidenceStatus, VERIFIED)
        )).stream()
                .map(item -> scoreCase(item, resolution, requestedText, goal, relatedTagIds, caseTags, regions, regionId, provinceId))
                .filter(item -> item.relevance() > 0)
                .sorted(caseComparator())
                .toList();

        List<ScoredPolicy> relevantPolicies = safe(policyMapper.selectList(
                new LambdaQueryWrapper<Policy>()
                        .eq(Policy::getStatus, PUBLISHED)
                        .eq(Policy::getAiEvidenceStatus, VERIFIED)
        )).stream()
                .map(item -> scorePolicy(item, resolution, requestedText, goal, relatedTagIds, policyTags, regions, regionId, provinceId))
                .filter(item -> item.relevance() > 0 && !"cross_region".equals(item.geographicLevel()))
                .sorted(policyComparator())
                .toList();

        Map<Long, Source> sources = new LinkedHashMap<>();
        int sourceRejected = 0;
        List<ScoredCase> cases = new ArrayList<>();
        for (ScoredCase candidate : relevantCases) {
            if (registerEligibleSource(candidate.item().getSourceId(), sources)) {
                if (cases.size() < CASE_LIMIT) {
                    cases.add(candidate);
                }
            } else {
                sourceRejected++;
            }
        }
        List<ScoredPolicy> policies = new ArrayList<>();
        for (ScoredPolicy candidate : relevantPolicies) {
            if (registerEligibleSource(candidate.item().getSourceId(), sources)) {
                if (policies.size() < POLICY_LIMIT) {
                    policies.add(candidate);
                }
            } else {
                sourceRejected++;
            }
        }

        Set<Long> usedSources = new LinkedHashSet<>();
        cases.stream().map(item -> item.item().getSourceId()).filter(Objects::nonNull).forEach(usedSources::add);
        policies.stream().map(item -> item.item().getSourceId()).filter(Objects::nonNull).forEach(usedSources::add);
        sources.entrySet().removeIf(entry -> !usedSources.contains(entry.getKey()));

        List<String> reasons = new ArrayList<>();
        if (resolution.requiresConfirmation()) {
            reasons.add("行业标签匹配置信度较低，请确认行业");
        }
        if (cases.isEmpty()) {
            reasons.add("无已核验案例");
        }
        if (policies.isEmpty()) {
            reasons.add("无已核验政策");
        }
        if (sourceRejected > 0) {
            reasons.add("存在 " + sourceRejected + " 条相关资料的来源未核验");
        }
        boolean evidenceAvailable = !resolution.requiresConfirmation()
                && !cases.isEmpty()
                && !policies.isEmpty()
                && !sources.isEmpty();
        String hash = evidenceHash(resolution, cases, policies, sources);
        return new Assessment(
                requestedRegion,
                resolution,
                List.copyOf(cases),
                List.copyOf(policies),
                Map.copyOf(sources),
                List.copyOf(reasons),
                evidenceAvailable,
                aiClient.descriptor().available(),
                hash
        );
    }

    private Assessment emptyAssessment(Region region, IndustryResolution resolution, List<String> reasons) {
        return new Assessment(
                region, resolution, List.of(), List.of(), Map.of(), reasons,
                false, aiClient.descriptor().available(), sha256(region.getId() + ":unresolved")
        );
    }

    private EntrepreneurshipReadinessVO toReadiness(Assessment assessment) {
        EntrepreneurshipReadinessVO result = new EntrepreneurshipReadinessVO();
        result.setModelAvailable(assessment.modelAvailable());
        result.setEvidenceAvailable(assessment.evidenceAvailable());
        result.setResolvedIndustryTag(assessment.industry());
        result.setMatchMethod(assessment.industry().method());
        result.setConfidence(assessment.industry().confidence());
        result.setVerifiedCaseCount(assessment.cases().size());
        result.setVerifiedPolicyCount(assessment.policies().size());
        result.setVerifiedSourceCount(assessment.sources().size());
        result.setExactRegionCount(countLevel(assessment, "exact_region"));
        result.setParentRegionCount(countLevel(assessment, "parent_province"));
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
            Long requestedRegionId,
            Long provinceId
    ) {
        int relevance = relevance(
                searchable(item.getTitle(), item.getCategory(), item.getSummary(), item.getTags(), item.getBusinessModel(), item.getOutcome()),
                industryText,
                resolution.name(),
                goal,
                relations.getOrDefault(item.getId(), Set.of()),
                relatedTagIds
        );
        Geographic geographic = geography(item.getRegionId(), regions, requestedRegionId, provinceId);
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
            Long requestedRegionId,
            Long provinceId
    ) {
        int relevance = relevance(
                searchable(item.getTitle(), item.getPolicyType(), item.getSummary(), item.getTags(), item.getKeyPoints(), item.getSupportMeasures()),
                industryText,
                resolution.name(),
                goal,
                relations.getOrDefault(item.getId(), Set.of()),
                relatedTagIds
        );
        Geographic geographic = geography(item.getRegionId(), regions, requestedRegionId, provinceId);
        return new ScoredPolicy(item, relevance, geographic.rank(), geographic.level(), geographic.reason(), geographic.regionName());
    }

    private int relevance(
            String searchable,
            String requestedText,
            String canonicalName,
            String goal,
            Set<Long> itemTags,
            Set<Long> relatedTagIds
    ) {
        int score = itemTags.stream().anyMatch(relatedTagIds::contains) ? 50 : 0;
        score += textScore(searchable, requestedText, 20);
        if (!Objects.equals(requestedText, canonicalName)) {
            score += textScore(searchable, canonicalName, 20);
        }
        score += textScore(searchable, goal, 2);
        return score;
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
            Long requestedRegionId,
            Long provinceId
    ) {
        Region itemRegion = regions.get(itemRegionId);
        String name = itemRegion == null ? "未标注地区" : itemRegion.getName();
        if (Objects.equals(itemRegionId, requestedRegionId)) {
            return new Geographic(4, "exact_region", "当前地区直接匹配", name);
        }
        if (provinceId != null && Objects.equals(itemRegionId, provinceId)) {
            return new Geographic(3, "parent_province", "上级省份可用资料", name);
        }
        if (itemRegion != null && ("country".equalsIgnoreCase(itemRegion.getLevel()) || "中国".equals(itemRegion.getName()))) {
            return new Geographic(2, "national", "国家级通用资料", name);
        }
        return new Geographic(1, "cross_region", "跨地区借鉴案例，非本地案例", name);
    }

    private Map<Long, Region> regionMap(Region requested) {
        List<Region> values = regionMapper.selectList(new LambdaQueryWrapper<Region>());
        Map<Long, Region> result = safe(values).stream()
                .collect(Collectors.toMap(Region::getId, Function.identity(), (left, right) -> left, HashMap::new));
        result.put(requested.getId(), requested);
        return result;
    }

    private Long provinceId(Region requested, Map<Long, Region> regions) {
        Region current = requested;
        Set<Long> visited = new LinkedHashSet<>();
        while (current != null && current.getId() != null && visited.add(current.getId())) {
            if ("province".equalsIgnoreCase(current.getLevel())) {
                return current.getId();
            }
            current = current.getParentId() == null ? null : regions.get(current.getParentId());
        }
        return null;
    }

    private Map<Long, Set<Long>> caseTagsByItem() {
        return safe(caseTagMapper.selectList(new LambdaQueryWrapper<CaseTag>())).stream()
                .collect(Collectors.groupingBy(
                        CaseTag::getCaseId,
                        Collectors.mapping(CaseTag::getTagId, Collectors.toSet())
                ));
    }

    private Map<Long, Set<Long>> policyTagsByItem() {
        return safe(policyTagMapper.selectList(new LambdaQueryWrapper<PolicyTag>())).stream()
                .collect(Collectors.groupingBy(
                        PolicyTag::getPolicyId,
                        Collectors.mapping(PolicyTag::getTagId, Collectors.toSet())
                ));
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
                && StringUtils.hasText(source.getUrl());
    }

    private Comparator<ScoredCase> caseComparator() {
        return Comparator.comparingInt(ScoredCase::relevance).reversed()
                .thenComparing(Comparator.comparingInt(ScoredCase::geographicRank).reversed())
                .thenComparing(item -> date(item.item().getAccessedAt()), Comparator.reverseOrder())
                .thenComparing(item -> item.item().getId(), Comparator.reverseOrder());
    }

    private Comparator<ScoredPolicy> policyComparator() {
        return Comparator.comparingInt(ScoredPolicy::relevance).reversed()
                .thenComparing(Comparator.comparingInt(ScoredPolicy::geographicRank).reversed())
                .thenComparing(item -> date(item.item().getPublishDate()), Comparator.reverseOrder())
                .thenComparing(item -> item.item().getId(), Comparator.reverseOrder());
    }

    private LocalDate date(LocalDate value) {
        return value == null ? LocalDate.MIN : value;
    }

    private String evidenceHash(
            IndustryResolution industry,
            List<ScoredCase> cases,
            List<ScoredPolicy> policies,
            Map<Long, Source> sources
    ) {
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("industry", industry);
            content.put("cases", cases.stream().map(item -> List.of(
                    item.item().getId(), safe(item.item().getTitle()), safe(item.item().getSummary()),
                    safe(item.item().getBusinessModel()), safe(item.item().getOutcome()),
                    safe(item.item().getUpdatedAt() == null ? null : item.item().getUpdatedAt().toString())
            )).toList());
            content.put("policies", policies.stream().map(item -> List.of(
                    item.item().getId(), safe(item.item().getTitle()), safe(item.item().getSummary()),
                    safe(item.item().getKeyPoints()), safe(item.item().getSupportMeasures()),
                    safe(item.item().getUpdatedAt() == null ? null : item.item().getUpdatedAt().toString())
            )).toList());
            content.put("sources", sources.values().stream().map(source -> List.of(
                    source.getId(), safe(source.getTitle()), safe(source.getUrl()), safe(source.getNotes()),
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

    private <T> List<T> safe(Collection<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record Assessment(
            Region region,
            IndustryResolution industry,
            List<ScoredCase> cases,
            List<ScoredPolicy> policies,
            Map<Long, Source> sources,
            List<String> reasons,
            boolean evidenceAvailable,
            boolean modelAvailable,
            String hash
    ) {
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
}
