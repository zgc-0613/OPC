package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.vo.AgentEvidenceItemVO;
import com.opc.platform.ai.vo.AgentRunEvidenceVO;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
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
public class AgentRunEvidenceService {

    private static final int MAX_PER_TYPE = 12;
    private static final Set<String> ITEM_TYPES = Set.of("case", "policy", "source");

    private final AiAnalysisRunMapper runMapper;
    private final AiAgentToolCallMapper toolCallMapper;
    private final CaseItemMapper caseMapper;
    private final PolicyMapper policyMapper;
    private final SourceMapper sourceMapper;
    private final RegionMapper regionMapper;
    private final ObjectMapper objectMapper;

    public AgentRunEvidenceVO read(AuthenticatedUser user, Long runId) {
        if (user == null || user.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        AiAnalysisRun run = runMapper.selectOwnedAgentRun(runId, user.userId());
        if (run == null) throw new BusinessException(ErrorCode.NOT_FOUND, "研究运行不存在");

        List<EvidenceRef> references = extractReferences(toolCallMapper.selectByRunId(runId));
        Set<Long> caseIds = ids(references, "case");
        Set<Long> policyIds = ids(references, "policy");
        Map<Long, CaseItem> cases = caseIds.isEmpty()
                ? Map.of() : byId(caseMapper.selectBatchIds(caseIds), CaseItem::getId);
        Map<Long, Policy> policies = policyIds.isEmpty()
                ? Map.of() : byId(policyMapper.selectBatchIds(policyIds), Policy::getId);

        Set<Long> sourceIds = new LinkedHashSet<>(ids(references, "source"));
        references.stream().map(EvidenceRef::sourceId).filter(Objects::nonNull).forEach(sourceIds::add);
        cases.values().stream().map(CaseItem::getSourceId).filter(Objects::nonNull).forEach(sourceIds::add);
        policies.values().stream().map(Policy::getSourceId).filter(Objects::nonNull).forEach(sourceIds::add);
        Map<Long, Source> sources = sourceIds.isEmpty()
                ? Map.of() : byId(sourceMapper.selectBatchIds(sourceIds), Source::getId);

        Set<Long> regionIds = new LinkedHashSet<>();
        cases.values().stream().map(CaseItem::getRegionId).filter(Objects::nonNull).forEach(regionIds::add);
        policies.values().stream().map(Policy::getRegionId).filter(Objects::nonNull).forEach(regionIds::add);
        Map<Long, Region> regions = regionIds.isEmpty()
                ? Map.of() : byId(regionMapper.selectBatchIds(regionIds), Region::getId);

        List<AgentEvidenceItemVO> items = new ArrayList<>();
        for (EvidenceRef reference : references) {
            AgentEvidenceItemVO item = switch (reference.itemType()) {
                case "case" -> caseEvidence(reference, cases.get(reference.itemId()), sources, regions);
                case "policy" -> policyEvidence(reference, policies.get(reference.itemId()), sources, regions);
                case "source" -> sourceEvidence(reference, sources.get(reference.itemId()));
                default -> null;
            };
            if (item != null) items.add(item);
        }
        Map<String, Integer> groups = ITEM_TYPES.stream().collect(Collectors.toMap(
                Function.identity(),
                type -> (int) items.stream().filter(item -> type.equals(item.itemType())).count(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
        return new AgentRunEvidenceVO(runId, run.getStatus(), List.copyOf(items), Map.copyOf(groups));
    }

    private AgentEvidenceItemVO caseEvidence(
            EvidenceRef reference,
            CaseItem item,
            Map<Long, Source> sources,
            Map<Long, Region> regions
    ) {
        Source source = item == null ? null : sources.get(item.getSourceId());
        if (!eligible(item) || !eligible(source)) return unavailable(reference);
        Region region = regions.get(item.getRegionId());
        return available(
                "case", item.getId(), item.getSourceId(), item.getTitle(), item.getSummary(),
                region, item.getCategory(), reference.matchReason(), source,
                firstSafeUrl(item.getOriginalUrl(), source.getUrl()), "/cases/" + item.getId()
        );
    }

    private AgentEvidenceItemVO policyEvidence(
            EvidenceRef reference,
            Policy item,
            Map<Long, Source> sources,
            Map<Long, Region> regions
    ) {
        Source source = item == null ? null : sources.get(item.getSourceId());
        if (!eligible(item) || !eligible(source)) return unavailable(reference);
        Region region = regions.get(item.getRegionId());
        return available(
                "policy", item.getId(), item.getSourceId(), item.getTitle(), item.getSummary(),
                region, item.getPolicyType(), reference.matchReason(), source,
                firstSafeUrl(item.getOriginalUrl(), item.getEvidenceUrl(), source.getUrl()),
                "/policies/" + item.getId()
        );
    }

    private AgentEvidenceItemVO sourceEvidence(EvidenceRef reference, Source source) {
        if (!eligible(source)) return unavailable(reference);
        return new AgentEvidenceItemVO(
                "source", source.getId(), source.getId(), bounded(source.getTitle(), 240), "",
                "", "", bounded(source.getSourceType(), 80), bounded(reference.matchReason(), 240),
                "verified", bounded(source.getPublisher(), 160), bounded(source.getTitle(), 240),
                safeUrl(source.getUrl()), null, true
        );
    }

    private AgentEvidenceItemVO available(
            String itemType,
            Long itemId,
            Long sourceId,
            String title,
            String brief,
            Region region,
            String industry,
            String matchReason,
            Source source,
            String originalUrl,
            String detailUrl
    ) {
        return new AgentEvidenceItemVO(
                itemType, itemId, sourceId, bounded(title, 240), bounded(brief, 600),
                region == null ? "" : bounded(region.getName(), 100),
                region == null ? "" : bounded(region.getLevel(), 40),
                bounded(industry, 100), bounded(matchReason, 240), "verified",
                bounded(source.getPublisher(), 160), bounded(source.getTitle(), 240),
                originalUrl, detailUrl, true
        );
    }

    private AgentEvidenceItemVO unavailable(EvidenceRef reference) {
        return new AgentEvidenceItemVO(
                reference.itemType(), reference.itemId(), reference.sourceId(), "资料当前不可用", "",
                "", "", "", "状态已变化，请重新检索", "unavailable",
                "", "", null, null, false
        );
    }

    private List<EvidenceRef> extractReferences(List<AiAgentToolCall> calls) {
        Map<String, EvidenceRef> unique = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("case", 0);
        counts.put("policy", 0);
        counts.put("source", 0);
        for (AiAgentToolCall call : calls == null ? List.<AiAgentToolCall>of() : calls) {
            if (call == null || !"completed".equals(call.getStatus())) continue;
            JsonNode root = parse(call.getResultSummaryJson());
            switch (String.valueOf(call.getToolName())) {
                case "search_cases" -> appendArray(unique, counts, root.path("items"), "case", "caseId");
                case "search_policies" -> appendArray(unique, counts, root.path("items"), "policy", "policyId");
                case "compare_cases" -> appendArray(unique, counts, root.path("cases"), "case", "caseId");
                case "get_source" -> append(unique, counts, "source", longValue(root, "sourceId"),
                        longValue(root, "sourceId"), "已核验原始来源");
                default -> {
                    // Unknown and future tools do not expand the user evidence surface implicitly.
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private void appendArray(
            Map<String, EvidenceRef> unique,
            Map<String, Integer> counts,
            JsonNode items,
            String itemType,
            String idField
    ) {
        if (!items.isArray()) return;
        for (JsonNode item : items) {
            append(unique, counts, itemType, longValue(item, idField), longValue(item, "sourceId"),
                    bounded(item.path("matchReason").asText(""), 240));
        }
    }

    private void append(
            Map<String, EvidenceRef> unique,
            Map<String, Integer> counts,
            String itemType,
            Long itemId,
            Long sourceId,
            String matchReason
    ) {
        if (!ITEM_TYPES.contains(itemType) || itemId == null || itemId <= 0) return;
        String key = itemType + ':' + itemId;
        if (unique.containsKey(key) || counts.get(itemType) >= MAX_PER_TYPE) return;
        unique.put(key, new EvidenceRef(itemType, itemId, sourceId, matchReason));
        counts.put(itemType, counts.get(itemType) + 1);
    }

    private JsonNode parse(String value) {
        if (value == null || value.length() > 16000) return objectMapper.createObjectNode();
        try {
            JsonNode parsed = objectMapper.readTree(value);
            return parsed == null ? objectMapper.createObjectNode() : parsed;
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.canConvertToLong() && value.asLong() > 0 ? value.asLong() : null;
    }

    private Set<Long> ids(List<EvidenceRef> references, String type) {
        return references.stream().filter(reference -> type.equals(reference.itemType()))
                .map(EvidenceRef::itemId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private <T> Map<Long, T> byId(Collection<T> values, Function<T, Long> id) {
        if (values == null || values.isEmpty()) return Map.of();
        return values.stream().filter(Objects::nonNull).collect(Collectors.toMap(
                id, Function.identity(), (left, right) -> left, LinkedHashMap::new
        ));
    }

    private boolean eligible(CaseItem item) {
        return item != null && "published".equals(item.getStatus()) && "verified".equals(item.getAiEvidenceStatus());
    }

    private boolean eligible(Policy item) {
        return item != null && "published".equals(item.getStatus()) && "verified".equals(item.getAiEvidenceStatus());
    }

    private boolean eligible(Source item) {
        return item != null && "published".equals(item.getStatus()) && "verified".equals(item.getAiEvidenceStatus())
                && safeUrl(item.getUrl()) != null;
    }

    private String firstSafeUrl(String... values) {
        for (String value : values) {
            String safe = safeUrl(value);
            if (safe != null) return safe;
        }
        return null;
    }

    private String safeUrl(String value) {
        if (value == null || value.length() > 1000) return null;
        try {
            URI uri = URI.create(value.trim());
            if (!Set.of("http", "https").contains(uri.getScheme())
                    || uri.getHost() == null || uri.getUserInfo() != null) return null;
            return uri.toString();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String bounded(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.trim().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        if (normalized.codePointCount(0, normalized.length()) <= maxLength) return normalized;
        int end = normalized.offsetByCodePoints(0, maxLength);
        return normalized.substring(0, end);
    }

    private record EvidenceRef(String itemType, Long itemId, Long sourceId, String matchReason) {
    }
}
