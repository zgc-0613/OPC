package com.opc.platform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.security.ProviderEndpointPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDeepSeekSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "OPC_RUN_REAL_DEEPSEEK_EVAL", matches = "(?i)true")
    void realDeepSeekJsonPlanSmokeIsReportedSeparatelyFromDeterministicEvaluation() throws Exception {
        String apiKey = required("OPC_DEEPSEEK_API_KEY");
        String baseUrl = environment("OPC_DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1");
        String model = environment("OPC_DEEPSEEK_MODEL", "deepseek-chat");
        URI baseUri = URI.create(baseUrl);
        String origin = baseUri.getScheme() + "://" + baseUri.getHost();
        ObjectMapper objectMapper = new ObjectMapper();
        ProviderEndpointPolicy endpointPolicy = new ProviderEndpointPolicy(
                Set.of(origin), ProviderEndpointPolicy.HostResolver.system());
        AiHttpTransport transport = new ValidatingAiHttpTransport(
                new JavaNetAiHttpTransport(), endpointPolicy);
        AiRuntimeSettings settings = new AiRuntimeSettings(
                "deepseek", "openai_compatible", baseUrl, model, apiKey,
                0.0, 256, Duration.ofSeconds(45), 0, true);
        OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(settings, transport, objectMapper);
        String schema = """
                {"type":"object","additionalProperties":false,
                 "required":["action","toolName","arguments"],"properties":{
                   "action":{"const":"tool"},"toolName":{"const":"search_policies"},
                   "arguments":{"type":"object","additionalProperties":false,
                     "required":["regionId"],"properties":{
                       "regionId":{"type":"integer"},"query":{"type":"string","maxLength":120},
                       "limit":{"type":"integer","minimum":1,"maximum":10}}}}}
                """;
        AiProviderResponse response = client.generate(new AiProviderRequest(
                "agent-real-smoke", "agent-real-smoke-v1",
                "Return one bounded JSON tool plan. Do not answer the research question and do not invent tools.",
                "Choose search_policies for regionId 1, query artificial intelligence, limit 1.",
                schema
        ));

        assertEquals("stop", response.finishReason());
        assertTrue(response.promptTokens() > 0);
        assertTrue(response.completionTokens() > 0);
        assertEquals(response.promptTokens() + response.completionTokens(), response.totalTokens());
        JsonNode plan = objectMapper.readTree(response.content());
        assertEquals("tool", plan.path("action").asText());
        assertEquals("search_policies", plan.path("toolName").asText());
        assertEquals(1L, plan.path("arguments").path("regionId").asLong());
        assertEquals(1, plan.path("arguments").path("limit").asInt());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scope", "real_provider_smoke_not_model_quality_score");
        report.put("provider", "deepseek");
        report.put("model", model);
        report.put("finishReason", response.finishReason());
        report.put("providerRequestId", response.requestId() == null ? "not_provided" : response.requestId());
        report.put("promptTokens", response.promptTokens());
        report.put("completionTokens", response.completionTokens());
        report.put("totalTokens", response.totalTokens());
        report.put("latencyMs", response.latencyMs());
        String serialized = objectMapper.writeValueAsString(report);
        assertFalse(serialized.contains(apiKey));
        System.out.println("AGENT_REAL_DEEPSEEK_SMOKE=" + serialized);
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value.trim();
    }

    private String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
