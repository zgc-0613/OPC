package com.opc.platform.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleAiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsChatCompletionRequestAndCapturesSafeUsageMetadata() {
        CapturingTransport transport = new CapturingTransport(new AiHttpResponse(
                200,
                "{\"id\":\"req-body-1\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\"}}],"
                        + "\"usage\":{\"prompt_tokens\":19,\"completion_tokens\":7,\"total_tokens\":26}}",
                HttpHeaders.of(Map.of("x-request-id", List.of("req-header-1")), (a, b) -> true)
        ));
        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(
                settings(), transport, objectMapper
        );

        AiProviderResponse response = client.generate(request());

        assertEquals("{\"summary\":\"ok\"}", response.content());
        assertEquals(19, response.promptTokens());
        assertEquals(7, response.completionTokens());
        assertEquals(26, response.totalTokens());
        assertEquals("req-header-1", response.requestId());
        assertEquals("stop", response.finishReason());
        assertEquals("https://api.example.com/v1/chat/completions", transport.request.uri().toString());
        assertEquals("Bearer sk-provider-secret", transport.request.headers().firstValue("Authorization").orElseThrow());
        assertTrue(transport.request.body().contains("case-analysis-v1"));
        assertTrue(transport.request.body().contains("json_object"));
        assertFalse(transport.request.body().contains("sk-provider-secret"));
    }

    @Test
    void capturesLengthFinishReasonWithoutDiscardingUsage() {
        CapturingTransport transport = new CapturingTransport(new AiHttpResponse(
                200,
                "{\"id\":\"req-length\",\"choices\":[{\"finish_reason\":\"length\","
                        + "\"message\":{\"content\":\"partial\"}}],"
                        + "\"usage\":{\"prompt_tokens\":3513,\"completion_tokens\":1199,\"total_tokens\":4712}}",
                HttpHeaders.of(Map.of(), (a, b) -> true)
        ));
        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(settings(), transport, objectMapper);

        AiProviderResponse response = client.generate(request());

        assertEquals("length", response.finishReason());
        assertEquals(3513, response.promptTokens());
        assertEquals(1199, response.completionTokens());
        assertEquals("req-length", response.requestId());
    }

    @Test
    void mapsProviderNeutralToolsAndParsesNativeToolCalls() throws Exception {
        CapturingTransport transport = new CapturingTransport(new AiHttpResponse(
                200,
                "{\"id\":\"req-tool\",\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{" +
                        "\"content\":null,\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\"," +
                        "\"function\":{\"name\":\"search_cases\",\"arguments\":\"{\\\"regionId\\\":1}\"}}]}}]," +
                        "\"usage\":{\"prompt_tokens\":30,\"completion_tokens\":9,\"total_tokens\":39}}",
                HttpHeaders.of(Map.of("x-request-id", List.of("req-tool-header")), (a, b) -> true)
        ));
        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(settings(), transport, objectMapper);
        AiProviderRequest request = new AiProviderRequest(
                "agent-research",
                "agent-v1",
                "Use tools only.",
                "Find cases.",
                null,
                List.of(
                        AiProviderMessage.system("Use tools only."),
                        AiProviderMessage.user("Find cases.")
                ),
                List.of(new AiToolDefinition(
                        "search_cases", "Search verified cases",
                        "{\"type\":\"object\",\"properties\":{\"regionId\":{\"type\":\"integer\"}}}"
                )),
                false
        );

        AiProviderResponse response = client.generate(request);

        assertEquals("tool_calls", response.finishReason());
        assertEquals(1, response.toolCalls().size());
        assertEquals("call-1", response.toolCalls().get(0).id());
        assertEquals("search_cases", response.toolCalls().get(0).name());
        assertEquals(1, objectMapper.readTree(response.toolCalls().get(0).argumentsJson()).path("regionId").asInt());
        assertEquals(39, response.totalTokens());
        var body = objectMapper.readTree(transport.request.body());
        assertEquals("function", body.path("tools").get(0).path("type").asText());
        assertEquals("search_cases", body.path("tools").get(0).path("function").path("name").asText());
        assertTrue(body.path("response_format").isMissingNode());
    }

    @Test
    void retriesOnlyRetryableErrorsAndNeverLeaksApiKey() {
        AiHttpTransport transport = request -> new AiHttpResponse(
                401,
                "{\"error\":{\"message\":\"invalid key sk-provider-secret\"}}",
                HttpHeaders.of(Map.of(), (a, b) -> true)
        );
        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(settings(), transport, objectMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> client.generate(request()));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
        assertFalse(exception.getMessage().contains("sk-provider-secret"));
        assertFalse(exception.getMessage().contains("invalid key"));
    }

    @Test
    void mapsRateLimitServerFailureAndTimeoutToStableDiagnostics() {
        record FailureCase(AiHttpTransport transport, String diagnostic) {}
        List<FailureCase> failures = List.of(
                new FailureCase(request -> new AiHttpResponse(
                        429, "{}", HttpHeaders.of(Map.of(), (a, b) -> true)), "PROVIDER_RATE_LIMIT"),
                new FailureCase(request -> new AiHttpResponse(
                        503, "{}", HttpHeaders.of(Map.of(), (a, b) -> true)), "PROVIDER_5XX"),
                new FailureCase(request -> { throw new HttpTimeoutException("timed out"); }, "PROVIDER_TIMEOUT")
        );

        for (FailureCase failure : failures) {
            AiProviderException exception = assertThrows(
                    AiProviderException.class,
                    () -> new OpenAiCompatibleAiClient(settings(), failure.transport(), objectMapper).generate(request())
            );
            assertEquals(failure.diagnostic(), exception.getDiagnosticCode());
            assertFalse(exception.getMessage().contains("sk-provider-secret"));
        }
    }

    private AiRuntimeSettings settings() {
        return new AiRuntimeSettings(
                "deepseek",
                "openai_compatible",
                "https://api.example.com/v1",
                "configured-model-id",
                "sk-provider-secret",
                0.2,
                1200,
                Duration.ofSeconds(15),
                1,
                true
        );
    }

    private AiProviderRequest request() {
        return new AiProviderRequest(
                "case-analysis",
                "case-analysis-v1",
                "Use only supplied evidence.",
                "Analyze case 1.",
                "{\"type\":\"object\"}"
        );
    }

    private static final class CapturingTransport implements AiHttpTransport {
        private final AiHttpResponse response;
        private AiHttpRequest request;

        private CapturingTransport(AiHttpResponse response) {
            this.response = response;
        }

        @Override
        public AiHttpResponse execute(AiHttpRequest request) {
            this.request = request;
            return response;
        }
    }
}
