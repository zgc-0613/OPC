package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.tool.AgentTool;
import com.opc.platform.ai.tool.AgentToolContext;
import com.opc.platform.ai.tool.AgentToolRegistry;
import com.opc.platform.ai.tool.AgentToolResult;
import com.opc.platform.ai.tool.SearchCasesArguments;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentContractReplayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> sanitizedProviderFailuresKeepStableDiagnostics() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/ai/agent-v2-contract-replays.json")) {
            JsonNode fixtures = objectMapper.readTree(stream);
            return java.util.stream.StreamSupport.stream(fixtures.spliterator(), false)
                    .map(fixture -> DynamicTest.dynamicTest(
                            fixture.path("id").asText(), () -> assertReplay(fixture)))
                    .toList().stream();
        }
    }

    private void assertReplay(JsonNode fixture) throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        AgentToolRegistry registry = registry(toolCalls);
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>();
        if ("synthesis".equals(fixture.path("phase").asText())) {
            responses.add(response(plan().toString(), "stop"));
            responses.add(response(payload(fixture, finalResult()).toString(),
                    fixture.path("finishReason").asText("stop")));
        } else {
            String content = fixture.has("raw")
                    ? fixture.path("raw").asText()
                    : payload(fixture, plan()).toString();
            responses.add(response(content, fixture.path("finishReason").asText("stop")));
        }
        int rounds = "synthesis".equals(fixture.path("phase").asText()) ? 2 : 1;
        AgentOrchestrator orchestrator = new AgentOrchestrator(objectMapper, registry);

        AgentOrchestratorException exception = assertThrows(
                AgentOrchestratorException.class,
                () -> orchestrator.execute(
                        new AgentOrchestratorInput(
                                91L, 42L, "{\"regionId\":1,\"industry\":\"AI\"}",
                                "sanitized replay", List.of(),
                                new AgentRuntimeConfig(true, rounds, 4, 8000, 4,
                                        Duration.ofSeconds(30), "json_plan")
                        ),
                        request -> {
                            providerCalls.incrementAndGet();
                            return responses.removeFirst();
                        },
                        progress -> { }
                )
        );

        assertEquals(fixture.path("diagnostic").asText(), exception.getDiagnosticCode());
        assertEquals(fixture.path("providerCalls").asInt(), providerCalls.get());
        assertEquals(fixture.path("toolCalls").asInt(), toolCalls.get());
    }

    private ObjectNode payload(JsonNode fixture, ObjectNode base) {
        JsonNode shape = fixture.path("shape");
        if (shape.isObject()) shape.fields().forEachRemaining(entry -> base.set(entry.getKey(), entry.getValue()));
        return base;
    }

    private ObjectNode plan() {
        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("action", "plan").put("intent", "case_analysis");
        plan.putArray("researchQuestions").add("本地证据能否支持研究问题？");
        plan.putArray("toolRequests").addObject()
                .put("requestId", "cases").put("toolName", "search_cases")
                .set("arguments", objectMapper.createObjectNode());
        ((ObjectNode) plan.path("toolRequests").get(0)).putArray("dependsOn");
        plan.putArray("comparisonDimensions");
        plan.putArray("outputSections").add("directAnswer").add("citations");
        return plan;
    }

    private ObjectNode finalResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("action", "final").put("intent", "case_analysis")
                .put("directAnswer", "基于已核验证据给出结论。");
        result.putArray("keyFindings").addObject().put("text", "存在一个本地案例")
                .put("evidenceType", "fact").putArray("sourceIds").add(1);
        result.putArray("caseInsights");
        result.putArray("policyInsights");
        result.putArray("comparison");
        result.putArray("recommendations");
        result.putArray("risks");
        result.putArray("assumptions");
        result.putArray("uncertainties");
        result.putArray("nextQuestions");
        result.putArray("citations").addObject().put("sourceId", 1).put("claim", "来源支持结论");
        result.put("confidence", 0.7);
        result.putObject("evidenceCoverage").put("status", "partial")
                .put("caseCount", 1).put("policyCount", 0).put("sourceCount", 1)
                .putArray("limitations");
        return result;
    }

    private AgentToolRegistry registry(AtomicInteger executions) {
        AgentTool<SearchCasesArguments> search = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "sanitized replay search"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() { return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}"; }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                executions.incrementAndGet();
                ObjectNode output = objectMapper.createObjectNode();
                output.putArray("items").addObject().put("caseId", 11).put("sourceId", 1)
                        .put("geographicScope", "exact");
                return new AgentToolResult(output, 1, "a".repeat(64), Set.of(1L), Set.of(11L));
            }
        };
        AiAgentToolCallMapper mapper = mock(AiAgentToolCallMapper.class);
        when(mapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId(1L);
            return 1;
        });
        when(mapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        return new AgentToolRegistry(
                List.of(search), objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), mapper
        );
    }

    private AiProviderResponse response(String content, String finishReason) {
        return new AiProviderResponse(content, 4, 3, 7, 5, "replay-request", finishReason);
    }
}
