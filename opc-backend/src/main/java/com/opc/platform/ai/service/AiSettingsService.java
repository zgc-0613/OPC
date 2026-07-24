package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.AiModelDiscoveryRequestDTO;
import com.opc.platform.ai.dto.AiModelOptionDTO;
import com.opc.platform.ai.dto.AiModelSettingsUpdateDTO;
import com.opc.platform.ai.entity.AiModelSettings;
import com.opc.platform.ai.entity.AiSettingsAudit;
import com.opc.platform.ai.mapper.AiModelSettingsMapper;
import com.opc.platform.ai.mapper.AiSettingsAuditMapper;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiHttpRequest;
import com.opc.platform.ai.provider.AiHttpResponse;
import com.opc.platform.ai.provider.AiHttpTransport;
import com.opc.platform.ai.provider.AiProviderFactory;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.ai.security.AesGcmSecretCipher;
import com.opc.platform.ai.security.ProviderEndpointPolicy;
import com.opc.platform.ai.vo.AiConnectionTestVO;
import com.opc.platform.ai.vo.AiModelSettingsVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiSettingsService implements AiRuntimeSettingsProvider {

    private static final long SETTINGS_ID = 1L;
    private static final Set<String> FORMATS = Set.of(
            "openai_responses",
            "openai_compatible",
            "anthropic",
            "amazon_bedrock",
            "google_gemini"
    );

    private final AiModelSettingsMapper settingsMapper;
    private final AiSettingsAuditMapper auditMapper;
    private final AesGcmSecretCipher cipher;
    private final AiProviderFactory providerFactory;
    private final ObjectMapper objectMapper;
    private final AiHttpTransport transport;
    private final ProviderEndpointPolicy endpointPolicy;

    public AiModelSettingsVO get() {
        return toVO(loadOrDefault());
    }

    @Transactional
    public AiModelSettingsVO update(AiModelSettingsUpdateDTO dto, AuthenticatedAdmin admin) {
        validate(dto);
        AiModelSettings current = settingsMapper.selectById(SETTINGS_ID);
        boolean insert = current == null;
        if (insert) {
            current = defaults();
        }
        String provider = normalize(dto.getProvider());
        String apiBaseUrl = trimToNull(dto.getApiBaseUrl());
        ProviderEndpointPolicy.ValidatedEndpoint endpoint = apiBaseUrl == null
                ? null
                : endpointPolicy.validate(provider, apiBaseUrl);
        boolean keyUpdated = StringUtils.hasText(dto.getApiKey());
        if (keyUpdated) {
            current.setApiKeyCiphertext(cipher.encrypt(dto.getApiKey().trim()));
            current.setApiKeyProvider(provider);
            current.setApiKeyOrigin(endpoint == null ? null : endpoint.origin());
        } else if (StringUtils.hasText(current.getApiKeyCiphertext())) {
            requireMatchingKeyBinding(current, provider, endpoint);
            if (!StringUtils.hasText(current.getApiKeyProvider()) && endpoint != null) {
                current.setApiKeyProvider(provider);
                current.setApiKeyOrigin(endpoint.origin());
            }
        }
        if (Boolean.TRUE.equals(dto.getEnabled())) {
            requireRunnable(dto, current);
        }
        current.setProvider(provider);
        current.setApiFormat(normalize(dto.getApiFormat()));
        current.setApiBaseUrl(apiBaseUrl);
        current.setModelId(trimToNull(dto.getModelId()));
        current.setModelCatalogJson(writeModelCatalog(dto.getModels()));
        current.setTemperature(dto.getTemperature());
        current.setMaxOutputTokens(dto.getMaxOutputTokens());
        current.setTimeoutSeconds(dto.getTimeoutSeconds());
        current.setRetryCount(dto.getRetryCount());
        current.setDailyTokenQuota(dto.getDailyTokenQuota());
        current.setEnabled(dto.getEnabled());
        current.setUpdatedByAdminId(admin.adminId());
        current.setUpdatedByAdminUsername(admin.username());
        if (insert) {
            settingsMapper.insert(current);
        } else {
            settingsMapper.updateById(current);
        }
        audit(admin, "update", "configuration updated; apiKeyUpdated=" + keyUpdated, true);
        return toVO(current);
    }

    @Transactional
    public AiConnectionTestVO testConnection(AuthenticatedAdmin admin) {
        AiModelSettings stored = settingsMapper.selectById(SETTINGS_ID);
        LocalDateTime testedAt = LocalDateTime.now();
        if (stored == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI provider is not configured");
        }
        try {
            AiRuntimeSettings runtime = runtimeFromStored(stored, true);
            AiRuntimeSettings lowCost = new AiRuntimeSettings(
                    runtime.provider(), runtime.apiFormat(), runtime.apiBaseUrl(), runtime.model(), runtime.apiKey(),
                    0, 1, runtime.timeout(), 0, runtime.enabled()
            );
            AiClient client = providerFactory.create(lowCost);
            client.generate(new AiProviderRequest(
                    "connection-test",
                    "connection-test-v1",
                    "Return only a small JSON object.",
                    "Return {\"ok\":true}.",
                    "{\"type\":\"object\"}"
            ));
            updateTestState(stored, "success", "连接成功", testedAt);
            audit(admin, "test_connection", "connection succeeded", true);
            return new AiConnectionTestVO(true, "连接成功", testedAt);
        } catch (BusinessException exception) {
            String message = sanitize(exception.getMessage());
            updateTestState(stored, "failed", message, testedAt);
            audit(admin, "test_connection", "connection failed", false);
            throw new BusinessException(exception.getErrorCode(), message);
        }
    }

    public List<AiModelOptionDTO> discoverModels(
            AiModelDiscoveryRequestDTO dto,
            AuthenticatedAdmin admin
    ) {
        if (!"deepseek".equalsIgnoreCase(dto.getProvider())
                || !"openai_compatible".equalsIgnoreCase(dto.getApiFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Selected provider cannot discover models");
        }
        ProviderEndpointPolicy.ValidatedEndpoint endpoint = endpointPolicy.validate(
                dto.getProvider(), dto.getApiBaseUrl()
        );
        String apiKey = trimToNull(dto.getApiKey());
        if (apiKey == null) {
            AiModelSettings stored = settingsMapper.selectById(SETTINGS_ID);
            if (stored == null || !StringUtils.hasText(stored.getApiKeyCiphertext())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请先填写 API Key 或保存密钥");
            }
            requireMatchingKeyBinding(stored, normalize(dto.getProvider()), endpoint);
            apiKey = cipher.decrypt(stored.getApiKeyCiphertext());
        }

        try {
            HttpHeaders headers = HttpHeaders.of(
                    Map.of(
                            "Authorization", List.of("Bearer " + apiKey),
                            "Accept", List.of("application/json")
                    ),
                    (name, value) -> true
            );
            AiHttpResponse response = transport.execute(new AiHttpRequest(
                    "GET",
                    URI.create(joinUrl(endpoint.baseUri().toString(), "models")),
                    headers,
                    "",
                    Duration.ofSeconds(dto.getTimeoutSeconds() == null ? 30 : dto.getTimeoutSeconds())
            ));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "模型列表获取失败 (HTTP " + response.statusCode() + ")");
            }
            List<AiModelOptionDTO> models = parseDiscoveredModels(response.body());
            audit(admin, "discover_models", "model discovery succeeded; count=" + models.size(), true);
            return models;
        } catch (BusinessException exception) {
            audit(admin, "discover_models", "model discovery failed", false);
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            audit(admin, "discover_models", "model discovery interrupted", false);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "模型列表获取失败");
        } catch (Exception exception) {
            audit(admin, "discover_models", "model discovery failed", false);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "模型列表获取失败");
        }
    }

    @Override
    public AiRuntimeSettings current() {
        AiModelSettings stored = settingsMapper.selectById(SETTINGS_ID);
        if (stored == null || !Boolean.TRUE.equals(stored.getEnabled())) {
            return disabled(stored);
        }
        return runtimeFromStored(stored, true);
    }

    private AiRuntimeSettings runtimeFromStored(AiModelSettings stored, boolean enabled) {
        if (!StringUtils.hasText(stored.getApiBaseUrl())
                || !StringUtils.hasText(stored.getModelId())
                || !StringUtils.hasText(stored.getApiKeyCiphertext())) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI provider is not configured");
        }
        ProviderEndpointPolicy.ValidatedEndpoint endpoint = endpointPolicy.validate(
                stored.getProvider(), stored.getApiBaseUrl()
        );
        requireMatchingKeyBinding(stored, normalize(stored.getProvider()), endpoint);
        String apiKey = cipher.decrypt(stored.getApiKeyCiphertext());
        return new AiRuntimeSettings(
                stored.getProvider(), stored.getApiFormat(), stored.getApiBaseUrl(), stored.getModelId(), apiKey,
                value(stored.getTemperature(), 0.2), value(stored.getMaxOutputTokens(), 1200),
                Duration.ofSeconds(value(stored.getTimeoutSeconds(), 30)), value(stored.getRetryCount(), 1), enabled
        );
    }

    @Override
    public long dailyTokenQuota() {
        AiModelSettings stored = settingsMapper.selectById(SETTINGS_ID);
        return stored == null || stored.getDailyTokenQuota() == null ? 100_000L : stored.getDailyTokenQuota();
    }

    private AiRuntimeSettings disabled(AiModelSettings stored) {
        return new AiRuntimeSettings(
                stored == null ? "disabled" : stored.getProvider(),
                stored == null ? "openai_compatible" : stored.getApiFormat(),
                null,
                stored == null || !StringUtils.hasText(stored.getModelId()) ? "unconfigured" : stored.getModelId(),
                null,
                0.2,
                1200,
                Duration.ofSeconds(30),
                0,
                false
        );
    }

    private void validate(AiModelSettingsUpdateDTO dto) {
        String format = dto.getApiFormat().trim().toLowerCase();
        if (!FORMATS.contains(format)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported AI API format");
        }
        if (!"deepseek".equalsIgnoreCase(dto.getProvider())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported AI provider");
        }
    }

    private void requireRunnable(AiModelSettingsUpdateDTO dto, AiModelSettings current) {
        if (!"openai_compatible".equalsIgnoreCase(dto.getApiFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Selected AI API format is not supported yet");
        }
        if (!StringUtils.hasText(dto.getApiBaseUrl()) || !StringUtils.hasText(dto.getModelId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "API Base URL and Model ID are required when enabled");
        }
        if (!StringUtils.hasText(dto.getApiKey()) && !StringUtils.hasText(current.getApiKeyCiphertext())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "API Key is required when enabled");
        }
        if (!cipher.available()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI settings master key is not configured");
        }
    }

    private void requireMatchingKeyBinding(
            AiModelSettings current,
            String provider,
            ProviderEndpointPolicy.ValidatedEndpoint endpoint
    ) {
        if (endpoint == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已保存 API Key 需要匹配的 HTTPS 地址");
        }
        String boundProvider = StringUtils.hasText(current.getApiKeyProvider())
                ? normalize(current.getApiKeyProvider())
                : normalize(current.getProvider());
        String boundOrigin = current.getApiKeyOrigin();
        if (!StringUtils.hasText(boundOrigin) && StringUtils.hasText(current.getApiBaseUrl())) {
            boundOrigin = endpointPolicy.validate(current.getProvider(), current.getApiBaseUrl()).origin();
        }
        if (!provider.equals(boundProvider) || !endpoint.origin().equals(boundOrigin)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "更换 Provider 或 API Base URL 后必须重新输入 API Key");
        }
    }

    private AiModelSettings loadOrDefault() {
        AiModelSettings stored = settingsMapper.selectById(SETTINGS_ID);
        return stored == null ? defaults() : stored;
    }

    private AiModelSettings defaults() {
        AiModelSettings settings = new AiModelSettings();
        settings.setId(SETTINGS_ID);
        settings.setProvider("deepseek");
        settings.setApiFormat("openai_compatible");
        settings.setTemperature(0.2);
        settings.setMaxOutputTokens(1200);
        settings.setTimeoutSeconds(30);
        settings.setRetryCount(1);
        settings.setDailyTokenQuota(100_000L);
        settings.setEnabled(false);
        settings.setLastTestStatus("not_tested");
        return settings;
    }

    private AiModelSettingsVO toVO(AiModelSettings settings) {
        AiModelSettingsVO vo = new AiModelSettingsVO();
        vo.setProvider(settings.getProvider());
        vo.setApiFormat(settings.getApiFormat());
        vo.setApiBaseUrl(settings.getApiBaseUrl());
        vo.setModelId(settings.getModelId());
        vo.setModels(readModelCatalog(settings));
        vo.setApiKeyConfigured(StringUtils.hasText(settings.getApiKeyCiphertext()));
        vo.setEncryptionReady(cipher.available());
        vo.setTemperature(settings.getTemperature());
        vo.setMaxOutputTokens(settings.getMaxOutputTokens());
        vo.setTimeoutSeconds(settings.getTimeoutSeconds());
        vo.setRetryCount(settings.getRetryCount());
        vo.setDailyTokenQuota(settings.getDailyTokenQuota());
        vo.setEnabled(settings.getEnabled());
        vo.setLastTestStatus(settings.getLastTestStatus());
        vo.setLastTestedAt(settings.getLastTestedAt());
        vo.setLastTestMessage(settings.getLastTestMessage());
        vo.setUpdatedByAdminUsername(settings.getUpdatedByAdminUsername());
        vo.setUpdatedAt(settings.getUpdatedAt());
        return vo;
    }

    private String writeModelCatalog(List<AiModelOptionDTO> models) {
        if (models == null || models.isEmpty()) {
            return null;
        }
        Map<String, AiModelOptionDTO> unique = new LinkedHashMap<>();
        for (AiModelOptionDTO model : models) {
            if (model == null || !StringUtils.hasText(model.modelId())) {
                continue;
            }
            String modelId = model.modelId().trim();
            String displayName = StringUtils.hasText(model.displayName()) ? model.displayName().trim() : modelId;
            unique.putIfAbsent(modelId, new AiModelOptionDTO(modelId, displayName));
        }
        try {
            return unique.isEmpty() ? null : objectMapper.writeValueAsString(unique.values());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid AI model catalog");
        }
    }

    private List<AiModelOptionDTO> readModelCatalog(AiModelSettings settings) {
        if (StringUtils.hasText(settings.getModelCatalogJson())) {
            try {
                return objectMapper.readValue(settings.getModelCatalogJson(), new TypeReference<>() {});
            } catch (JsonProcessingException ignored) {
                // The active model remains usable even if the optional display catalog is damaged.
            }
        }
        if (StringUtils.hasText(settings.getModelId())) {
            return List.of(new AiModelOptionDTO(settings.getModelId(), settings.getModelId()));
        }
        return List.of();
    }

    private List<AiModelOptionDTO> parseDiscoveredModels(String body) throws JsonProcessingException {
        JsonNode data = objectMapper.readTree(body).path("data");
        if (!data.isArray()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "模型列表响应格式无效");
        }
        Map<String, AiModelOptionDTO> unique = new LinkedHashMap<>();
        for (JsonNode item : data) {
            String modelId = item.path("id").asText("").trim();
            if (!modelId.isEmpty() && modelId.length() <= 191) {
                unique.putIfAbsent(modelId, new AiModelOptionDTO(modelId, modelId));
            }
            if (unique.size() >= 100) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    private String joinUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        return normalizedBase + "/" + path;
    }

    private void updateTestState(AiModelSettings settings, String status, String message, LocalDateTime testedAt) {
        settings.setLastTestStatus(status);
        settings.setLastTestedAt(testedAt);
        settings.setLastTestMessage(message);
        settingsMapper.updateById(settings);
    }

    private void audit(AuthenticatedAdmin admin, String action, String summary, boolean success) {
        AiSettingsAudit audit = new AiSettingsAudit();
        audit.setAdminId(admin.adminId());
        audit.setAdminUsername(admin.username());
        audit.setAction(action);
        audit.setChangeSummary(summary);
        audit.setSuccess(success);
        auditMapper.insert(audit);
    }

    private String sanitize(String message) {
        if (!StringUtils.hasText(message)) {
            return "AI provider request failed";
        }
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private double value(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
