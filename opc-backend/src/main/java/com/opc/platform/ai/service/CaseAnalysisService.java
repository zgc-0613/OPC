package com.opc.platform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.CaseAnalysisRequestDTO;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
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
import org.springframework.dao.DuplicateKeyException;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseAnalysisService {

    private static final String PUBLISHED = "published";
    private static final String VERIFIED = "verified";
    private static final String PROMPT_VERSION = "case-analysis-v1";

    private final CaseItemMapper caseItemMapper;
    private final SourceMapper sourceMapper;
    private final PolicyMapper policyMapper;
    private final AiAnalysisRunMapper runMapper;
    private final AiClient aiClient;
    private final AiRuntimeSettingsProvider settingsProvider;
    private final ObjectMapper objectMapper;

    public CaseAnalysisVO analyze(AuthenticatedUser user, CaseAnalysisRequestDTO request) {
        CaseItem caseItem = caseItemMapper.selectById(request.getCaseId());
        if (caseItem == null || !PUBLISHED.equals(caseItem.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "案例不存在或尚未发布");
        }

        if (!VERIFIED.equals(caseItem.getAiEvidenceStatus())) {
            return insufficient(user, caseItem, "legacy_unverified");
        }

        EvidenceBundle evidence = loadEvidence(caseItem);
        if (evidence.sources().isEmpty()) {
            return insufficient(user, caseItem, "insufficient");
        }

        enforceLimits(user.userId());
        AiProviderDescriptor descriptor = aiClient.descriptor();
        if (!descriptor.available()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "智能体模型尚未配置或未启用");
        }

        AiAnalysisRun run = startRun(user, caseItem, descriptor, evidence.hash());
        try {
            AiProviderResponse providerResponse = aiClient.generate(new AiProviderRequest(
                    "case-analysis",
                    PROMPT_VERSION,
                    systemPrompt(),
                    userPrompt(caseItem, evidence, request.getUserQuestion()),
                    responseSchema()
            ));
            ModelPayload payload = parse(providerResponse.content());
            List<AiCitationVO> citations = validateCitations(payload.getCitations(), evidence.sources());
            CaseAnalysisVO result = toResult(run, caseItem, descriptor, providerResponse, payload, citations);
            complete(run, providerResponse, providerResponse.content());
            return result;
        } catch (BusinessException exception) {
            fail(run, exception.getErrorCode().name());
            throw exception;
        } catch (RuntimeException exception) {
            fail(run, "UNEXPECTED_PROVIDER_RESPONSE");
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 返回内容格式无效，请稍后重试");
        }
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
        String hash = sha256(caseItem.getId() + ":" + sources.keySet() + ":" + usablePolicies.stream().map(Policy::getId).toList());
        return new EvidenceBundle(sources, usablePolicies, hash);
    }

    private void enforceLimits(Long userId) {
        long used = value(runMapper.sumCompletedTokensToday(userId));
        long dailyTokenQuota = settingsProvider.dailyTokenQuota();
        if (dailyTokenQuota > 0 && used >= dailyTokenQuota) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "今日智能体词元额度已用完");
        }
        if (runMapper.countRunningForUser(userId) > 0) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "已有案例分析正在进行，请勿重复提交");
        }
    }

    private AiAnalysisRun startRun(
            AuthenticatedUser user,
            CaseItem caseItem,
            AiProviderDescriptor descriptor,
            String evidenceHash
    ) {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setUserId(user.userId());
        run.setCaseId(caseItem.getId());
        run.setStatus("running");
        run.setProvider(descriptor.provider());
        run.setModelId(descriptor.model());
        run.setPromptVersion(PROMPT_VERSION);
        run.setEvidenceHash(evidenceHash);
        try {
            runMapper.insert(run);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "已有案例分析正在进行，请勿重复提交");
        }
        return run;
    }

    private CaseAnalysisVO insufficient(AuthenticatedUser user, CaseItem caseItem, String evidenceStatus) {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setUserId(user.userId());
        run.setCaseId(caseItem.getId());
        run.setStatus("evidence_insufficient");
        run.setProvider("not_called");
        run.setModelId("not_called");
        run.setPromptVersion(PROMPT_VERSION);
        run.setEvidenceHash(sha256(caseItem.getId() + ":" + evidenceStatus));
        run.setResultJson("{\"evidenceStatus\":\"insufficient\"}");
        run.setPromptTokens(0);
        run.setCompletionTokens(0);
        run.setTotalTokens(0);
        runMapper.insert(run);

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

    private ModelPayload parse(String content) {
        try {
            ModelPayload payload = objectMapper.readValue(content, ModelPayload.class);
            if (!StringUtils.hasText(payload.getSummary())
                    || !StringUtils.hasText(payload.getBusinessModel())
                    || !StringUtils.hasText(payload.getTechnicalAssessment())
                    || payload.getConfidence() == null
                    || payload.getConfidence() < 0
                    || payload.getConfidence() > 1) {
                throw invalidResponse();
            }
            payload.normalize();
            return payload;
        } catch (JsonProcessingException exception) {
            throw invalidResponse();
        }
    }

    private List<AiCitationVO> validateCitations(
            List<ModelCitation> citations,
            Map<Long, Source> sources
    ) {
        List<AiCitationVO> validated = new ArrayList<>();
        for (ModelCitation citation : citations) {
            Source source = sources.get(citation.getSourceId());
            if (source == null || !StringUtils.hasText(citation.getClaim())) {
                throw invalidResponse();
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
        result.setEvidenceStatus(citations.isEmpty() ? "insufficient" : "sufficient");
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

    private void complete(AiAnalysisRun run, AiProviderResponse response, String resultJson) {
        run.setStatus("completed");
        run.setResultJson(resultJson);
        run.setPromptTokens(response.promptTokens());
        run.setCompletionTokens(response.completionTokens());
        run.setTotalTokens(response.totalTokens());
        run.setLatencyMs(response.latencyMs());
        run.setProviderRequestId(response.requestId());
        runMapper.updateById(run);
    }

    private void fail(AiAnalysisRun run, String errorType) {
        run.setStatus("failed");
        run.setErrorType(errorType);
        runMapper.updateById(run);
    }

    private boolean eligible(Source source) {
        return source != null
                && PUBLISHED.equals(source.getStatus())
                && VERIFIED.equals(source.getAiEvidenceStatus())
                && StringUtils.hasText(source.getTitle())
                && StringUtils.hasText(source.getUrl());
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
                {"type":"object","required":["summary","businessModel","technicalAssessment","opportunities","risks","recommendedActions","citations","confidence"]}
                """;
    }

    private BusinessException invalidResponse() {
        return new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 返回内容格式无效，请稍后重试");
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

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
