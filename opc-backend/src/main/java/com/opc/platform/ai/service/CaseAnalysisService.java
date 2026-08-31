package com.opc.platform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.CaseAnalysisRequestDTO;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.exception.AiResponseValidationException;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.vo.AiCitationVO;
import com.opc.platform.ai.vo.AiTokenUsageVO;
import com.opc.platform.ai.vo.CaseAnalysisVO;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseAnalysisService {

    private static final String PUBLISHED = "published";
    private static final String VERIFIED = "verified";
    private static final String PROMPT_VERSION = "case-analysis-v1";
    private static final Set<String> RESPONSE_FIELDS = Set.of(
            "summary", "businessModel", "technicalAssessment", "opportunities", "risks",
            "recommendedActions", "citations", "confidence"
    );
    private static final Set<String> CITATION_FIELDS = Set.of("sourceId", "claim");

    private final CaseItemMapper caseItemMapper;
    private final SourceMapper sourceMapper;
    private final PolicyMapper policyMapper;
    private final AiAnalysisRunMapper runMapper;
    private final ObjectMapper objectMapper;
    private final AiTaskExecutionService taskExecutionService;

    public CaseAnalysisVO analyze(AuthenticatedUser user, CaseAnalysisRequestDTO request) {
        CaseItem caseItem = caseItemMapper.selectById(request.getCaseId());
        if (caseItem == null || !PUBLISHED.equals(caseItem.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "案例不存在或尚未发布");
        }

        if (!VERIFIED.equals(caseItem.getAiEvidenceStatus())) {
            return insufficient(user, caseItem, "case_unverified");
        }

        EvidenceBundle evidence = loadEvidence(caseItem);
        if (evidence.sources().isEmpty()) {
            return insufficient(user, caseItem, "source_chain_missing");
        }

        return taskExecutionService.execute(
                new AiTaskExecutionService.Task(user, "case_analysis", caseItem.getId(), PROMPT_VERSION, evidence.hash()),
                new AiProviderRequest(
                    "case-analysis",
                    PROMPT_VERSION,
                    systemPrompt(),
                    userPrompt(caseItem, evidence, request.getUserQuestion()),
                    responseSchema()
                ),
                execution -> {
                    ModelPayload payload = parse(execution.response());
                    List<AiCitationVO> citations = validateCitations(payload.getCitations(), evidence.sources());
                    requireEvidenceUnchanged(caseItem, evidence);
                    return toResult(
                            execution.run(), caseItem, execution.descriptor(), execution.response(), payload, citations
                    );
                }
        );
    }

    private EvidenceBundle loadEvidence(CaseItem caseItem) {
        Map<Long, Source> sources = new LinkedHashMap<>();
        Source caseSource = sourceMapper.selectById(caseItem.getSourceId());
        if (eligible(caseSource)) {
            sources.put(caseSource.getId(), caseSource);
        }

        List<Policy> policies = policyMapper.selectList(
                new LambdaQueryWrapper<Policy>()
                        .eq(Policy::getStatus, PUBLISHED)
                        .eq(Policy::getAiEvidenceStatus, VERIFIED)
                        .and(wrapper -> wrapper
                                .eq(Policy::getRegionId, caseItem.getRegionId())
                                .or()
                                .eq(Policy::getSourceId, caseItem.getSourceId()))
                        .orderByDesc(Policy::getPublishDate)
                        .last("LIMIT 5")
        );
        if (policies == null) {
            policies = List.of();
        }
        Set<Long> policySourceIds = policies.stream()
                .map(Policy::getSourceId)
                .filter(Objects::nonNull)
                .filter(id -> !sources.containsKey(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!policySourceIds.isEmpty()) {
            List<Source> relatedSources = sourceMapper.selectBatchIds(policySourceIds);
            if (relatedSources != null) {
                relatedSources.stream().filter(this::eligible).forEach(source -> sources.put(source.getId(), source));
            }
        }
        List<Policy> usablePolicies = policies.stream()
                .filter(policy -> sources.containsKey(policy.getSourceId()))
                .toList();
        String hash = evidenceHash(caseItem, sources, usablePolicies, "verified");
        return new EvidenceBundle(sources, usablePolicies, hash);
    }

    private CaseAnalysisVO insufficient(AuthenticatedUser user, CaseItem caseItem, String evidenceState) {
        String hash = evidenceHash(caseItem, Map.of(), List.of(), evidenceState);
        AiAnalysisRun run = runMapper.findRecentEvidenceInsufficient(
                user.userId(), "case_analysis", caseItem.getId(), hash);
        if (run == null) {
            run = new AiAnalysisRun();
            run.setUserId(user.userId());
            run.setTaskType("case_analysis");
            run.setCaseId(caseItem.getId());
            run.setStatus("evidence_insufficient");
            run.setProvider("not_called");
            run.setModelId("not_called");
            run.setPromptVersion(PROMPT_VERSION);
            run.setEvidenceHash(hash);
            run.setResultJson("{\"evidenceStatus\":\"insufficient\"}");
            run.setPromptTokens(0);
            run.setCompletionTokens(0);
            run.setTotalTokens(0);
            runMapper.insert(run);
        }
        CaseAnalysisVO result = new CaseAnalysisVO();
        result.setAnalysisId(run.getId());
        result.setCaseId(caseItem.getId());
        result.setSummary("证据不足");
        result.setBusinessModel("当前案例尚未完成 AI 证据核验。");
        result.setTechnicalAssessment("证据不足，暂不生成技术判断。");
        result.setEvidenceStatus("insufficient");
        result.setProvider("not_called");
        result.setModel("not_called");
        result.setPromptVersion(PROMPT_VERSION);
        result.setConfidence(0.0);
        result.setGeneratedAt(LocalDateTime.now());
        result.setTokenUsage(new AiTokenUsageVO(0, 0, 0));
        return result;
    }

    private ModelPayload parse(AiProviderResponse response) {
        if ("length".equalsIgnoreCase(response.finishReason())) {
            throw new AiResponseValidationException("TRUNCATED_RESPONSE");
        }
        if ("content_filter".equalsIgnoreCase(response.finishReason())) {
            throw new AiResponseValidationException("CONTENT_FILTERED");
        }
        if (!"stop".equalsIgnoreCase(response.finishReason())) {
            throw new AiResponseValidationException("ABNORMAL_FINISH_REASON");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(stripJsonFence(response.content()));
        } catch (JsonProcessingException exception) {
            throw invalidResponse("INVALID_JSON");
        }
        if (root == null || !root.isObject() || hasUnknownFields(root, RESPONSE_FIELDS)) {
            throw invalidResponse("INVALID_JSON");
        }
        if (!hasText(root, "summary", 500)
                || !hasText(root, "businessModel", 600)
                || !hasText(root, "technicalAssessment", 600)
                || !isBoundedStringArray(root.path("opportunities"), 5, 300)
                || !isBoundedStringArray(root.path("risks"), 5, 300)
                || !isBoundedStringArray(root.path("recommendedActions"), 5, 300)) {
            throw invalidResponse("MISSING_FIELD");
        }
        JsonNode citations = root.path("citations");
        if (!citations.isArray() || citations.isEmpty() || citations.size() > 8) {
            throw invalidResponse("MISSING_CITATIONS");
        }
        for (JsonNode citation : citations) {
            if (!citation.isObject() || hasUnknownFields(citation, CITATION_FIELDS)
                    || !citation.path("sourceId").isIntegralNumber()) {
                throw invalidResponse("UNKNOWN_SOURCE_ID");
            }
            if (!citation.path("claim").isTextual()
                    || !StringUtils.hasText(citation.path("claim").asText())
                    || citation.path("claim").asText().length() > 300) {
                throw invalidResponse("BLANK_CLAIM");
            }
        }
        JsonNode confidence = root.path("confidence");
        if (!confidence.isNumber()
                || !Double.isFinite(confidence.asDouble())
                || confidence.asDouble() < 0
                || confidence.asDouble() > 1) {
            throw invalidResponse("INVALID_CONFIDENCE");
        }
        try {
            ModelPayload payload = objectMapper.treeToValue(root, ModelPayload.class);
            payload.normalize();
            return payload;
        } catch (JsonProcessingException exception) {
            throw invalidResponse("INVALID_JSON");
        }
    }

    private List<AiCitationVO> validateCitations(
            List<ModelCitation> citations,
            Map<Long, Source> sources
    ) {
        if (citations == null || citations.isEmpty()) {
            throw invalidResponse("MISSING_CITATIONS");
        }
        List<AiCitationVO> validated = new ArrayList<>();
        for (ModelCitation citation : citations) {
            Source source = sources.get(citation.getSourceId());
            if (source == null) {
                throw invalidResponse("UNKNOWN_SOURCE_ID");
            }
            if (!StringUtils.hasText(citation.getClaim())) {
                throw invalidResponse("BLANK_CLAIM");
            }
            validated.add(new AiCitationVO(
                    source.getId(),
                    source.getTitle(),
                    source.getUrl(),
                    citation.getClaim().trim()
            ));
        }
        return validated;
    }

    private CaseAnalysisVO toResult(
            AiAnalysisRun run,
            CaseItem caseItem,
            AiProviderDescriptor descriptor,
            AiProviderResponse providerResponse,
            ModelPayload payload,
            List<AiCitationVO> citations
    ) {
        CaseAnalysisVO result = new CaseAnalysisVO();
        result.setAnalysisId(run.getId());
        result.setCaseId(caseItem.getId());
        result.setSummary(payload.getSummary());
        result.setBusinessModel(payload.getBusinessModel());
        result.setTechnicalAssessment(payload.getTechnicalAssessment());
        result.setOpportunities(payload.getOpportunities());
        result.setRisks(payload.getRisks());
        result.setRecommendedActions(payload.getRecommendedActions());
        result.setCitations(citations);
        result.setConfidence(payload.getConfidence());
        result.setEvidenceStatus("sufficient");
        result.setProvider(descriptor.provider());
        result.setModel(descriptor.model());
        result.setPromptVersion(PROMPT_VERSION);
        result.setGeneratedAt(LocalDateTime.now());
        result.setTokenUsage(new AiTokenUsageVO(
                providerResponse.promptTokens(),
                providerResponse.completionTokens(),
                providerResponse.totalTokens()
        ));
        return result;
    }

    private boolean eligible(Source source) {
        return source != null
                && PUBLISHED.equals(source.getStatus())
                && VERIFIED.equals(source.getAiEvidenceStatus())
                && StringUtils.hasText(source.getTitle())
                && StringUtils.hasText(source.getPublisher())
                && StringUtils.hasText(source.getUrl());
    }

    private void requireEvidenceUnchanged(CaseItem original, EvidenceBundle evidence) {
        CaseItem currentCase = caseItemMapper.selectById(original.getId());
        if (currentCase == null || !sameVersion(original, currentCase)
                || !PUBLISHED.equals(currentCase.getStatus())
                || !VERIFIED.equals(currentCase.getAiEvidenceStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "分析期间案例证据已变更，请重新生成");
        }

        List<Policy> currentPolicies = new ArrayList<>(evidence.policies().size());
        for (Policy expectedPolicy : evidence.policies()) {
            Policy currentPolicy = policyMapper.selectById(expectedPolicy.getId());
            if (currentPolicy == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "分析期间政策证据已删除，请重新生成");
            }
            currentPolicies.add(currentPolicy);
        }
        currentPolicies.sort(java.util.Comparator.comparing(Policy::getId));
        if (!samePolicyVersions(evidence.policies(), currentPolicies)
                || currentPolicies.stream().anyMatch(policy -> !PUBLISHED.equals(policy.getStatus())
                || !VERIFIED.equals(policy.getAiEvidenceStatus()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "分析期间政策证据已变更，请重新生成");
        }

        Map<Long, Source> currentSources = evidence.sources().keySet().stream()
                .sorted()
                .map(sourceMapper::selectById)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(Source::getId, source -> source, (left, right) -> left,
                        LinkedHashMap::new));
        if (currentSources.size() != evidence.sources().size()
                || !sameSourceVersions(new ArrayList<>(evidence.sources().values()), new ArrayList<>(currentSources.values()))
                || currentSources.values().stream().anyMatch(source -> !eligible(source))) {
            throw new BusinessException(ErrorCode.CONFLICT, "分析期间来源证据已变更，请重新生成");
        }
        if (!Objects.equals(evidence.hash(), evidenceHash(currentCase, currentSources, currentPolicies, "verified"))) {
            throw new BusinessException(ErrorCode.CONFLICT, "分析期间证据版本已变更，请重新生成");
        }
    }

    private boolean sameVersion(CaseItem left, CaseItem right) {
        return Objects.equals(left.getStatus(), right.getStatus())
                && Objects.equals(left.getAiEvidenceStatus(), right.getAiEvidenceStatus())
                && Objects.equals(revision(left.getEvidenceRevision()), revision(right.getEvidenceRevision()))
                && Objects.equals(left.getUpdatedAt(), right.getUpdatedAt());
    }

    private boolean sameVersion(Policy left, Policy right) {
        return Objects.equals(left.getStatus(), right.getStatus())
                && Objects.equals(left.getAiEvidenceStatus(), right.getAiEvidenceStatus())
                && Objects.equals(revision(left.getEvidenceRevision()), revision(right.getEvidenceRevision()))
                && Objects.equals(left.getUpdatedAt(), right.getUpdatedAt());
    }

    private boolean sameVersion(Source left, Source right) {
        return Objects.equals(left.getStatus(), right.getStatus())
                && Objects.equals(left.getAiEvidenceStatus(), right.getAiEvidenceStatus())
                && Objects.equals(revision(left.getEvidenceRevision()), revision(right.getEvidenceRevision()))
                && Objects.equals(left.getUpdatedAt(), right.getUpdatedAt());
    }

    private boolean samePolicyVersions(List<Policy> expected, List<Policy> actual) {
        if (expected.size() != actual.size()) return false;
        List<Policy> sortedExpected = expected.stream().sorted(java.util.Comparator.comparing(Policy::getId)).toList();
        return java.util.stream.IntStream.range(0, sortedExpected.size())
                .allMatch(index -> sameVersion(sortedExpected.get(index), actual.get(index)));
    }

    private boolean sameSourceVersions(List<Source> expected, List<Source> actual) {
        if (expected.size() != actual.size()) return false;
        List<Source> sortedExpected = expected.stream().sorted(java.util.Comparator.comparing(Source::getId)).toList();
        List<Source> sortedActual = actual.stream().sorted(java.util.Comparator.comparing(Source::getId)).toList();
        return java.util.stream.IntStream.range(0, sortedExpected.size())
                .allMatch(index -> sameVersion(sortedExpected.get(index), sortedActual.get(index)));
    }

    private String systemPrompt() {
        return """
                你是 SoloFirm 案例分析助手。只能使用服务端提供的已核验证据。
                必须返回严格 JSON；事实性结论必须给出 citation.sourceId。
                证据不足时减少置信度，不得编造来源、数字或政策。
                """;
    }

    private String userPrompt(CaseItem item, EvidenceBundle evidence, String userQuestion) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("case", Map.of(
                    "id", item.getId(),
                    "title", safe(item.getTitle()),
                    "articleTitle", safe(item.getArticleTitle()),
                    "category", safe(item.getCategory()),
                    "subcategory", safe(item.getSubcategory()),
                    "summary", safe(item.getSummary()),
                    "businessModel", safe(item.getBusinessModel()),
                    "aiTools", safe(item.getAiTools()),
                    "outcome", safe(item.getOutcome())
            ));
            payload.put("sources", evidence.sources().values().stream().map(source -> Map.of(
                    "sourceId", source.getId(),
                    "title", source.getTitle(),
                    "url", source.getUrl(),
                    "notes", safe(source.getNotes())
            )).toList());
            payload.put("policies", evidence.policies().stream().map(policy -> Map.of(
                    "id", policy.getId(),
                    "sourceId", policy.getSourceId(),
                    "title", safe(policy.getTitle()),
                    "summary", safe(policy.getSummary()),
                    "keyPoints", safe(policy.getKeyPoints())
            )).toList());
            if (StringUtils.hasText(userQuestion)) {
                payload.put("boundedQuestion", userQuestion.trim());
            }
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "案例证据无法序列化");
        }
    }

    private String responseSchema() {
        return """
                {
                  "type":"object",
                  "additionalProperties":false,
                  "required":["summary","businessModel","technicalAssessment","opportunities","risks","recommendedActions","citations","confidence"],
                  "properties":{
                    "summary":{"type":"string","minLength":1,"maxLength":500},
                    "businessModel":{"type":"string","minLength":1,"maxLength":600},
                    "technicalAssessment":{"type":"string","minLength":1,"maxLength":600},
                    "opportunities":{"type":"array","maxItems":5,"items":{"type":"string","minLength":1,"maxLength":300}},
                    "risks":{"type":"array","maxItems":5,"items":{"type":"string","minLength":1,"maxLength":300}},
                    "recommendedActions":{"type":"array","maxItems":5,"items":{"type":"string","minLength":1,"maxLength":300}},
                    "citations":{"type":"array","minItems":1,"maxItems":8,"items":{
                      "type":"object",
                      "additionalProperties":false,
                      "required":["sourceId","claim"],
                      "properties":{
                        "sourceId":{"type":"integer"},
                        "claim":{"type":"string","minLength":1,"maxLength":300}
                      }
                    }},
                    "confidence":{"type":"number","minimum":0,"maximum":1}
                  }
                }
                """;
    }

    private AiResponseValidationException invalidResponse() {
        return invalidResponse("INVALID_JSON");
    }

    private AiResponseValidationException invalidResponse(String diagnosticCode) {
        return new AiResponseValidationException(diagnosticCode);
    }

    private boolean hasText(JsonNode node, String field, int maxLength) {
        return node.path(field).isTextual()
                && StringUtils.hasText(node.path(field).asText())
                && node.path(field).asText().length() <= maxLength;
    }

    private boolean isBoundedStringArray(JsonNode node, int maxItems, int maxLength) {
        if (!node.isArray() || node.size() > maxItems) return false;
        for (JsonNode value : node) {
            if (!value.isTextual() || !StringUtils.hasText(value.asText()) || value.asText().length() > maxLength) {
                return false;
            }
        }
        return true;
    }

    private boolean hasUnknownFields(JsonNode node, Set<String> allowedFields) {
        var fields = node.fieldNames();
        while (fields.hasNext()) {
            if (!allowedFields.contains(fields.next())) return true;
        }
        return false;
    }

    private String stripJsonFence(String content) {
        String value = safe(content).trim();
        if (!value.startsWith("```")) return value;
        int firstLineEnd = value.indexOf('\n');
        if (firstLineEnd < 0 || !value.endsWith("```")) {
            throw invalidResponse("INVALID_JSON");
        }
        String marker = value.substring(0, firstLineEnd).trim().toLowerCase();
        if (!"```".equals(marker) && !"```json".equals(marker)) {
            throw invalidResponse("INVALID_JSON");
        }
        return value.substring(firstLineEnd + 1, value.length() - 3).trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Evidence hash failed");
        }
    }

    private String evidenceHash(
            CaseItem caseItem,
            Map<Long, Source> sources,
            List<Policy> policies,
            String evidenceState
    ) {
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("evidenceState", evidenceState);
            content.put("case", List.of(
                    caseItem.getId(), safe(caseItem.getTitle()), safe(caseItem.getSummary()),
                    safe(caseItem.getBusinessModel()), safe(caseItem.getAiTools()), safe(caseItem.getOutcome()),
                    safe(caseItem.getStatus()), safe(caseItem.getAiEvidenceStatus()),
                    revision(caseItem.getEvidenceRevision()),
                    safe(caseItem.getUpdatedAt() == null ? null : caseItem.getUpdatedAt().toString())
            ));
            content.put("policies", policies.stream().sorted(java.util.Comparator.comparing(Policy::getId)).map(policy -> List.of(
                    policy.getId(), safe(policy.getTitle()), safe(policy.getSummary()), safe(policy.getKeyPoints()),
                    safe(policy.getSupportMeasures()), safe(policy.getStatus()), safe(policy.getAiEvidenceStatus()),
                    revision(policy.getEvidenceRevision()),
                    safe(policy.getUpdatedAt() == null ? null : policy.getUpdatedAt().toString())
            )).toList());
            content.put("sources", sources.values().stream().sorted(java.util.Comparator.comparing(Source::getId)).map(source -> List.of(
                    source.getId(), safe(source.getTitle()), safe(source.getPublisher()), safe(source.getUrl()), safe(source.getNotes()),
                    safe(source.getStatus()), safe(source.getAiEvidenceStatus()), revision(source.getEvidenceRevision()),
                    safe(source.getUpdatedAt() == null ? null : source.getUpdatedAt().toString())
            )).toList());
            return sha256(objectMapper.writeValueAsString(content));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "案例证据版本无法计算");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private long revision(Long value) {
        return value == null ? 0L : value;
    }

    private record EvidenceBundle(
            Map<Long, Source> sources,
            List<Policy> policies,
            String hash
    ) {
    }

    @Data
    public static class ModelPayload {
        private String summary;
        private String businessModel;
        private String technicalAssessment;
        private List<String> opportunities;
        private List<String> risks;
        private List<String> recommendedActions;
        private List<ModelCitation> citations;
        private Double confidence;

        void normalize() {
            opportunities = clean(opportunities);
            risks = clean(risks);
            recommendedActions = clean(recommendedActions);
            citations = citations == null ? List.of() : citations;
        }

        private List<String> clean(Collection<String> values) {
            return values == null ? List.of() : values.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
        }
    }

    @Data
    public static class ModelCitation {
        private Long sourceId;
        private String claim;
    }
}
