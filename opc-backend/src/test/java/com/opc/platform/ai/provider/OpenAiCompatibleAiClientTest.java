package com.opc.platform.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
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
                "{\"id\":\"req-body-1\",\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\"}}],"
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
        assertEquals("https://api.example.com/v1/chat/completions", transport.request.uri().toString());
        assertEquals("Bearer sk-provider-secret", transport.request.headers().firstValue("Authorization").orElseThrow());
        assertTrue(transport.request.body().contains("case-analysis-v1"));
        assertTrue(transport.request.body().contains("json_object"));
        assertFalse(transport.request.body().contains("sk-provider-secret"));
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
