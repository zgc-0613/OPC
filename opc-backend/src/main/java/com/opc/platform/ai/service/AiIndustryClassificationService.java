package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.tag.service.IndustryTagService;
import com.opc.platform.tag.vo.IndustryResolution;
import com.opc.platform.tag.vo.IndustryTagVO;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AiIndustryClassificationService {

    private static final String TASK_TYPE = "industry_classification";
    private static final String PROMPT_VERSION = "industry-resolver-v2";
    private static final double CONFIRMATION_THRESHOLD = 0.75;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_CACHE_ENTRIES = 2048;

    private final IndustryTagService industryTagService;
    private final AiTaskExecutionService taskExecutionService;
    private final AiRuntimeSettingsProvider settingsProvider;
    private final ObjectMapper objectMapper;
    private final Map<CacheKey, CachedResolution> cache = new ConcurrentHashMap<>();

    public IndustryResolution classify(AuthenticatedUser user, String rawText) {
        if (user == null || user.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        if (!StringUtils.hasText(rawText)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入需要识别的行业");
        }
        IndustryResolution deterministic = industryTagService.resolve(null, rawText, false);
        if (deterministic.tagId() != null) {
            return deterministic;
        }

        List<IndustryTagVO> candidates = industryTagService.listIndustries().stream().limit(100).toList();
        if (candidates.isEmpty()) {
            return IndustryResolution.unresolved();
        }
        String normalized = IndustryTagService.normalize(rawText);
        AiRuntimeSettings runtime = settingsProvider.snapshot().settings();
        String catalogVersion = sha256(candidates.stream()
                .map(candidate -> candidate.tagId() + ":" + candidate.name())
                .collect(java.util.stream.Collectors.joining("|")));
        CacheKey cacheKey = new CacheKey(
                user.userId(), normalized, runtime.provider(), runtime.model(), catalogVersion
        );
        Instant now = Instant.now();
        purgeExpired(now);
        CachedResolution cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.resolution();
        }
        cache.remove(cacheKey, cached);

        String prompt = writePrompt(rawText, candidates);
        AiProviderRequest providerRequest = new AiProviderRequest(
                "industry-classification",
                PROMPT_VERSION,
                "将行业文本映射到给定候选标签。只能返回候选 tagId，不得创造标签，只返回 JSON。",
                prompt,
                "{\"type\":\"object\",\"required\":[\"tagId\",\"confidence\"]}"
        );
        IndustryResolution result = taskExecutionService.execute(
                new AiTaskExecutionService.Task(
                        user,
                        TASK_TYPE,
                        null,
                        PROMPT_VERSION,
                        sha256(normalized + ":" + candidates.stream().map(IndustryTagVO::tagId).toList())
                ),
                providerRequest,
                execution -> parse(execution.response().content(), rawText, candidates)
        );
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.entrySet().stream()
                    .min(Map.Entry.comparingByValue(
                            java.util.Comparator.comparing(CachedResolution::expiresAt)
                    ))
                    .map(Map.Entry::getKey)
                    .ifPresent(cache::remove);
        }
        cache.put(cacheKey, new CachedResolution(result, now.plus(CACHE_TTL)));
        return result;
    }

    private void purgeExpired(Instant now) {
        cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private IndustryResolution parse(String content, String rawText, List<IndustryTagVO> candidates) {
        try {
            JsonNode root = objectMapper.readTree(content);
            Long tagId = root.path("tagId").canConvertToLong() ? root.path("tagId").asLong() : null;
            double confidence = root.path("confidence").asDouble(-1);
            boolean allowed = candidates.stream().anyMatch(candidate -> candidate.tagId().equals(tagId));
            if (!allowed || confidence < 0 || confidence > 1) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 行业分类结果无效");
            }
            IndustryResolution selected = industryTagService.resolve(tagId, rawText, false);
            return new IndustryResolution(
                    selected.tagId(), selected.name(), selected.tagType(), "ai", confidence,
                    confidence < CONFIRMATION_THRESHOLD
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 行业分类结果无效");
        }
    }

    private String writePrompt(String rawText, List<IndustryTagVO> candidates) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "industryText", rawText.trim(),
                    "candidateTags", candidates.stream()
                            .map(candidate -> Map.of("tagId", candidate.tagId(), "name", candidate.name()))
                            .toList(),
                    "instruction", "只能从 candidateTags 选择一个 tagId，并返回 0 到 1 的 confidence"
            ));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "行业候选无法序列化");
        }
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "行业请求版本无法计算");
        }
    }

    private record CacheKey(
            Long userId,
            String normalizedText,
            String provider,
            String model,
            String catalogVersion
    ) {
    }

    private record CachedResolution(IndustryResolution resolution, Instant expiresAt) {
    }
}
