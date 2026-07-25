package com.opc.platform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpTimeoutException;
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
            } catch (HttpTimeoutException exception) {
                if (attempt == maxAttempts) {
                    throw providerFailure("PROVIDER_TIMEOUT", 0);
                }
            } catch (IOException exception) {
                if (attempt == maxAttempts) {
                    throw providerFailure("PROVIDER_NETWORK_ERROR", 0);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw providerFailure("PROVIDER_INTERRUPTED", 0);
            } catch (RuntimeException exception) {
                throw providerFailure("PROVIDER_INVALID_RESPONSE", 0);
            }
        }
        throw providerFailure("PROVIDER_NETWORK_ERROR", 0);
    }

    @Override
    public AiProviderDescriptor descriptor() {
        return new AiProviderDescriptor(settings.provider(), settings.model(), settings.enabled());
    }

    private AiHttpRequest buildRequest(AiProviderRequest request) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", settings.model());
            payload.put("messages", buildMessages(request));
            payload.put("temperature", settings.temperature());
            payload.put("max_tokens", settings.maxOutputTokens());
            if (request.jsonResponse()) {
                payload.put("response_format", Map.of("type", "json_object"));
            }
            if (!request.tools().isEmpty()) {
                payload.put("tools", request.tools().stream().map(this::mapTool).toList());
                payload.put("tool_choice", "auto");
            }
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
            throw providerFailure("PROVIDER_REQUEST_INVALID", 0);
        }
    }

    private AiProviderResponse parseResponse(AiHttpResponse response, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode message = root.path("choices").path(0).path("message");
            JsonNode content = message.path("content");
            List<AiProviderToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));
            if (!content.isTextual() && toolCalls.isEmpty()) {
                throw new IllegalArgumentException("Missing completion content and tool calls");
            }
            JsonNode usage = root.path("usage");
            String finishReason = root.path("choices").path(0).path("finish_reason").asText(null);
            String requestId = firstHeader(response.headers(), "x-request-id");
            if (requestId == null || requestId.isBlank()) {
                requestId = root.path("id").asText(null);
            }
            return new AiProviderResponse(
                    content.isTextual() ? content.asText() : "",
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0),
                    usage.path("total_tokens").asInt(0),
                    latencyMs,
                    requestId,
                    finishReason,
                    toolCalls
            );
        } catch (Exception exception) {
            throw providerFailure("PROVIDER_INVALID_RESPONSE", 502);
        }
    }

    private List<Map<String, Object>> buildMessages(AiProviderRequest request) {
        if (request.messages().isEmpty()) {
            return List.of(
                    Map.of(
                            "role", "system",
                            "content", systemContent(request, request.systemPrompt())
                    ),
                    Map.of("role", "user", "content", request.userPrompt())
            );
        }
        return request.messages().stream().map(message -> mapMessage(request, message)).toList();
    }

    private Map<String, Object> mapMessage(AiProviderRequest request, AiProviderMessage message) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("role", message.role());
        if ("system".equals(message.role())) {
            mapped.put("content", systemContent(request, message.content()));
        } else if (message.content() != null) {
            mapped.put("content", message.content());
        }
        if (message.toolCallId() != null) {
            mapped.put("tool_call_id", message.toolCallId());
        }
        if (!message.toolCalls().isEmpty()) {
            mapped.put("tool_calls", message.toolCalls().stream().map(call -> Map.of(
                    "id", call.id(),
                    "type", "function",
                    "function", Map.of("name", call.name(), "arguments", call.argumentsJson())
            )).toList());
        }
        return mapped;
    }

    private String systemContent(AiProviderRequest request, String content) {
        String value = "[" + request.promptVersion() + "] " + (content == null ? "" : content);
        if (request.jsonResponse() && request.responseSchema() != null) {
            value += "\nRequired response schema: " + request.responseSchema();
        }
        return value;
    }

    private Map<String, Object> mapTool(AiToolDefinition tool) {
        try {
            return Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", tool.name(),
                            "description", tool.description(),
                            "parameters", objectMapper.readTree(tool.parametersJson())
                    )
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid tool definition", exception);
        }
    }

    private List<AiProviderToolCall> parseToolCalls(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > 8) {
            throw new IllegalArgumentException("Invalid tool calls");
        }
        java.util.ArrayList<AiProviderToolCall> calls = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            String id = item.path("id").asText("").trim();
            String name = item.path("function").path("name").asText("").trim();
            String arguments = item.path("function").path("arguments").asText("").trim();
            if (id.isEmpty() || id.length() > 191 || name.isEmpty() || name.length() > 60
                    || arguments.isEmpty() || arguments.length() > 4000) {
                throw new IllegalArgumentException("Invalid tool call");
            }
            calls.add(new AiProviderToolCall(id, name, arguments));
        }
        return List.copyOf(calls);
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
        String diagnostic = statusCode == 429
                ? "PROVIDER_RATE_LIMIT"
                : statusCode >= 500 ? "PROVIDER_5XX" : "PROVIDER_HTTP_ERROR";
        return providerFailure(diagnostic, statusCode);
    }

    private AiProviderException providerFailure(String diagnosticCode, int statusCode) {
        String suffix = statusCode > 0 ? " (HTTP " + statusCode + ")" : "";
        return new AiProviderException(diagnosticCode, "AI provider request failed" + suffix);
    }
}
