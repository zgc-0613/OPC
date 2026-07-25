package com.opc.platform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OpenAiCompatibleAiClient implements AiClient {

    private final AiRuntimeSettings settings;
    private final AiHttpTransport transport;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleAiClient(
            AiRuntimeSettings settings,
            AiHttpTransport transport,
            ObjectMapper objectMapper
    ) {
        this.settings = settings;
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProviderResponse generate(AiProviderRequest request) {
        validateSettings();
        AiHttpRequest httpRequest = buildRequest(request);
        int maxAttempts = Math.max(1, settings.retryCount() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long started = System.nanoTime();
            try {
                AiHttpResponse response = transport.execute(httpRequest);
                long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseResponse(response, latencyMs);
                }
                if (!retryable(response.statusCode()) || attempt == maxAttempts) {
                    throw providerFailure(response.statusCode());
                }
            } catch (BusinessException exception) {
                throw exception;
            } catch (IOException exception) {
                if (attempt == maxAttempts) {
                    throw providerFailure(0);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw providerFailure(0);
            } catch (RuntimeException exception) {
                throw providerFailure(0);
            }
        }
        throw providerFailure(0);
    }

    @Override
    public AiProviderDescriptor descriptor() {
        return new AiProviderDescriptor(settings.provider(), settings.model(), settings.enabled());
    }

    private AiHttpRequest buildRequest(AiProviderRequest request) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", settings.model());
            payload.put("messages", List.of(
                    Map.of(
                            "role",
                            "system",
                            "content",
                            "[" + request.promptVersion() + "] " + request.systemPrompt()
                                    + "\nRequired response schema: " + request.responseSchema()
                    ),
                    Map.of("role", "user", "content", request.userPrompt())
            ));
            payload.put("temperature", settings.temperature());
            payload.put("max_tokens", settings.maxOutputTokens());
            payload.put("response_format", Map.of("type", "json_object"));
            HttpHeaders headers = HttpHeaders.of(
                    Map.of(
                            "Authorization", List.of("Bearer " + settings.apiKey()),
                            "Content-Type", List.of("application/json"),
                            "Accept", List.of("application/json")
                    ),
                    (name, value) -> true
            );
            return new AiHttpRequest(
                    URI.create(joinUrl(settings.apiBaseUrl(), "chat/completions")),
                    headers,
                    objectMapper.writeValueAsString(payload),
                    settings.timeout()
            );
        } catch (Exception exception) {
            throw providerFailure(0);
        }
    }

    private AiProviderResponse parseResponse(AiHttpResponse response, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw new IllegalArgumentException("Missing completion content");
            }
            JsonNode usage = root.path("usage");
            String finishReason = root.path("choices").path(0).path("finish_reason").asText(null);
            String requestId = firstHeader(response.headers(), "x-request-id");
            if (requestId == null || requestId.isBlank()) {
                requestId = root.path("id").asText(null);
            }
            return new AiProviderResponse(
                    content.asText(),
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0),
                    usage.path("total_tokens").asInt(0),
                    latencyMs,
                    requestId,
                    finishReason
            );
        } catch (Exception exception) {
            throw providerFailure(502);
        }
    }

    private void validateSettings() {
        if (!settings.enabled()
                || isBlank(settings.apiBaseUrl())
                || isBlank(settings.model())
                || isBlank(settings.apiKey())) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI provider is not configured");
        }
        if (!"openai_compatible".equalsIgnoreCase(settings.apiFormat())) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Selected AI API format is not supported yet");
        }
    }

    private boolean retryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private String joinUrl(String baseUrl, String suffix) {
        String normalized = baseUrl.trim();
        return normalized.endsWith("/") ? normalized + suffix : normalized + "/" + suffix;
    }

    private String firstHeader(HttpHeaders headers, String name) {
        return headers == null ? null : headers.firstValue(name.toLowerCase(Locale.ROOT)).orElse(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException providerFailure(int statusCode) {
        String suffix = statusCode > 0 ? " (HTTP " + statusCode + ")" : "";
        return new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI provider request failed" + suffix);
    }
}
