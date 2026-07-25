package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AiProviderException;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.tool.AgentTool;
import com.opc.platform.ai.tool.AgentToolContext;
import com.opc.platform.ai.tool.AgentToolException;
import com.opc.platform.ai.tool.AgentToolRegistry;
import com.opc.platform.ai.tool.AgentToolResult;
import com.opc.platform.ai.tool.CompareCasesArguments;
import com.opc.platform.ai.tool.SearchCasesArguments;
import com.opc.platform.ai.tool.SearchPoliciesArguments;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGoldenEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void twentyQuestionDeterministicEvaluationMeetsSafetyAndCitationMetrics() throws Exception {
        List<GoldenCase> cases = loadCases();
        assertEquals(20, cases.size());
        assertTrue(cases.stream().map(GoldenCase::category).distinct().count() >= 12);

        AgentClarificationPolicy clarificationPolicy = new AgentClarificationPolicy(objectMapper);
        AgentToolRegistry registry = registry();
        AgentOrchestrator orchestrator = new AgentOrchestrator(objectMapper, registry);
        int matched = 0;
        int completed = 0;
        int legalCitations = 0;
        int consistentCitations = 0;
        int acceptedUnknownSources = 0;
        int evidenceRefusals = 0;
        int allowedToolRequests = 0;
        int successfulToolCalls = 0;
        int totalRounds = 0;
        int totalToolCalls = 0;
        int totalTokens = 0;
        List<Long> latencies = new ArrayList<>();

        for (int index = 0; index < cases.size(); index++) {
            GoldenCase golden = cases.get(index);
            if (golden.kind().startsWith("clarification_")) {
                String profile = switch (golden.kind()) {
                    case "clarification_region" -> "{}";
                    case "clarification_industry" -> "{\"regionId\":1}";
                    default -> "{\"regionId\":1,\"industry\":\"AI\"}";
                };
                String question = clarificationPolicy.question(profile, golden.question());
                assertNotNull(question, golden.id());
                assertTrue(question.endsWith("？"), golden.id());
                matched++;
                continue;
            }

            ArrayDeque<AiProviderResponse> responses = responses(golden.kind());
            int expectedAllowedTools = expectedAllowedTools(golden.kind());
            allowedToolRequests += expectedAllowedTools;
            try {
                AgentOrchestratorOutcome outcome = orchestrator.execute(
                        new AgentOrchestratorInput(
                                100L + index, 42L, "{\"regionId\":1,\"industry\":\"AI\"}",
                                golden.question(), List.of(), config(golden.kind())
                        ),
                        request -> {
                            if ("provider_timeout".equals(golden.kind())) {
                                throw new AiProviderException("PROVIDER_TIMEOUT", "deterministic timeout");
                            }
                            return responses.removeFirst();
                        },
                        progress -> { }
                );
                if (golden.kind().startsWith("completed_")) {
                    assertEquals("completed", outcome.status(), golden.id());
                    assertFalse(outcome.citations().isEmpty(), golden.id());
                    completed++;
                    legalCitations += outcome.citations().stream().allMatch(c -> c.sourceId() == 1L)
                            ? outcome.citations().size() : 0;
                    consistentCitations += outcome.citations().stream().allMatch(c -> !c.claim().isBlank())
                            ? outcome.citations().size() : 0;
                } else {
                    assertEquals("evidence_insufficient", outcome.status(), golden.id());
                    assertTrue(outcome.citations().isEmpty(), golden.id());
                    evidenceRefusals++;
                }
                successfulToolCalls += outcome.toolCallCount();
                totalRounds += outcome.modelRounds();
                totalToolCalls += outcome.toolCallCount();
                totalTokens += outcome.totalTokens();
                latencies.add(outcome.latencyMs());
                matched++;
            } catch (AgentToolException exception) {
                assertEquals("unknown_tool", golden.kind(), golden.id());
                assertEquals("UNKNOWN_TOOL", exception.getDiagnosticCode(), golden.id());
                matched++;
            } catch (AgentOrchestratorException exception) {
                String expected = switch (golden.kind()) {
                    case "unknown_source" -> "UNKNOWN_SOURCE_ID";
                    case "truncated" -> "TRUNCATED_RESPONSE";
                    case "content_filter" -> "CONTENT_FILTERED";
                    default -> throw exception;
                };
                assertEquals(expected, exception.getDiagnosticCode(), golden.id());
                if ("unknown_source".equals(golden.kind())) acceptedUnknownSources = 0;
                matched++;
            } catch (AiProviderException exception) {
                assertEquals("provider_timeout", golden.kind(), golden.id());
                assertEquals("PROVIDER_TIMEOUT", exception.getDiagnosticCode(), golden.id());
                matched++;
            }
        }

        assertEquals(20, matched);
        assertEquals(7, completed);
        assertEquals(3, evidenceRefusals);
        assertEquals(0, acceptedUnknownSources);
        assertEquals(allowedToolRequests, successfulToolCalls);
        assertEquals(7, legalCitations);
        assertEquals(legalCitations, consistentCitations);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("questionCount", cases.size());
        metrics.put("taskCompletionRate", ratio(matched, cases.size()));
        metrics.put("legalCitationRate", ratio(legalCitations, completed));
        metrics.put("citationClaimConsistency", ratio(consistentCitations, legalCitations));
        metrics.put("unknownSourceCount", acceptedUnknownSources);
        metrics.put("toolCallSuccessRate", ratio(successfulToolCalls, allowedToolRequests));
        metrics.put("averageModelRounds", ratio(totalRounds, latencies.size()));
        metrics.put("averageToolCalls", ratio(totalToolCalls, latencies.size()));
        metrics.put("averageTokens", ratio(totalTokens, latencies.size()));
        metrics.put("p50LatencyMs", percentile(latencies, 0.50));
        metrics.put("p95LatencyMs", percentile(latencies, 0.95));
        metrics.put("evidenceInsufficientRefusalRate", ratio(evidenceRefusals, 3));
        System.out.println("AGENT_GOLDEN_METRICS=" + objectMapper.writeValueAsString(metrics));
    }

    private List<GoldenCase> loadCases() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/ai/agent-golden-evaluation.json")) {
            assertNotNull(stream);
            return objectMapper.readValue(stream, new TypeReference<>() { });
        }
    }

    private AgentToolRegistry registry() {
        AiAgentToolCallMapper mapper = mock(AiAgentToolCallMapper.class);
        AtomicLong ids = new AtomicLong(1);
        when(mapper.insert(any(AiAgentToolCall.class))).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId(ids.getAndIncrement());
            return 1;
        });
        return new AgentToolRegistry(
                List.of(policyTool(), caseTool(), compareTool()), objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), mapper
        );
    }

    private AgentTool<SearchPoliciesArguments> policyTool() {
        return tool("search_policies", SearchPoliciesArguments.class, (context, args) ->
                result(Map.of("items", List.of(Map.of("policyId", 21, "sourceId", 1))), Set.of(1L), Set.of()));
    }

    private AgentTool<SearchCasesArguments> caseTool() {
        return tool("search_cases", SearchCasesArguments.class, (context, args) ->
                result(Map.of("items", List.of(
                        Map.of("caseId", 11, "sourceId", 1), Map.of("caseId", 12, "sourceId", 1)
                )), Set.of(1L), Set.of(11L, 12L)));
    }

    private AgentTool<CompareCasesArguments> compareTool() {
        return tool("compare_cases", CompareCasesArguments.class, (context, args) -> {
            if (!context.allowedCaseIds().containsAll(args.getCaseIds())) {
                throw new AgentToolException("FORBIDDEN_CASE_ID", "not searched");
            }
            return result(Map.of("conclusions", List.of(
                    Map.of("caseId", 11, "sourceId", 1), Map.of("caseId", 12, "sourceId", 1)
            )), Set.of(1L), Set.copyOf(args.getCaseIds()));
        });
    }

    private <T> AgentTool<T> tool(String name, Class<T> type, ToolBody<T> body) {
        return new AgentTool<>() {
            public String name() { return name; }
            public String description() { return "deterministic golden tool"; }
            public Class<T> argumentType() { return type; }
            public String argumentSchema() { return "{\"type\":\"object\"}"; }
            public AgentToolResult execute(AgentToolContext context, T arguments) { return body.run(context, arguments); }
        };
    }

    private AgentToolResult result(Object output, Set<Long> sources, Set<Long> cases) {
        return new AgentToolResult(
                objectMapper.valueToTree(output), sources.size() + cases.size(),
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", sources, cases
        );
    }

    private ArrayDeque<AiProviderResponse> responses(String kind) {
        List<AiProviderResponse> values = switch (kind) {
            case "completed_policy" -> List.of(toolResponse("search_policies", "{\"regionId\":1,\"industry\":\"AI\"}"), finalResponse());
            case "completed_case" -> List.of(toolResponse("search_cases", "{\"regionId\":1,\"limit\":2}"), finalResponse());
            case "completed_compare" -> List.of(
                    toolResponse("search_cases", "{\"regionId\":1,\"limit\":2}"),
                    toolResponse("compare_cases", "{\"caseIds\":[11,12],\"dimensions\":[\"businessModel\"]}"),
                    finalResponse());
            case "evidence_insufficient" -> List.of(
                    toolResponse("search_policies", "{\"regionId\":1,\"limit\":5}"),
                    response("{\"action\":\"evidence_insufficient\",\"answer\":\"No adequate verified evidence.\",\"citations\":[],\"confidence\":0.2}", "stop"));
            case "unknown_tool" -> List.of(toolResponse("delete_database", "{}"));
            case "unknown_source" -> List.of(
                    toolResponse("search_policies", "{\"regionId\":1}"),
                    response("{\"action\":\"final\",\"answer\":\"Unsafe.\",\"citations\":[{\"sourceId\":999,\"claim\":\"Unknown\"}],\"confidence\":0.5}", "stop"));
            case "truncated" -> List.of(response("{\"action\":\"final\"", "length"));
            case "content_filter" -> List.of(response("{}", "content_filter"));
            case "provider_timeout" -> List.of();
            default -> throw new IllegalArgumentException(kind);
        };
        return new ArrayDeque<>(values);
    }

    private AiProviderResponse toolResponse(String tool, String arguments) {
        return response("{\"action\":\"tool\",\"toolName\":\"" + tool + "\",\"arguments\":" + arguments + "}", "stop");
    }

    private AiProviderResponse finalResponse() {
        return response("{\"action\":\"final\",\"answer\":\"Verified finding.\",\"citations\":[{\"sourceId\":1,\"claim\":\"The verified source supports this finding.\"}],\"confidence\":0.8}", "stop");
    }

    private AiProviderResponse response(String content, String finishReason) {
        return new AiProviderResponse(content, 10, 5, 15, 20, "req-golden", finishReason);
    }

    private AgentRuntimeConfig config(String kind) {
        return new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan");
    }

    private int expectedAllowedTools(String kind) {
        return switch (kind) {
            case "completed_policy", "completed_case" -> 1;
            case "completed_compare" -> 2;
            case "evidence_insufficient" -> 1;
            default -> 0;
        };
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round((numerator * 10000.0) / denominator) / 10000.0;
    }

    private long percentile(List<Long> values, double percentile) {
        List<Long> sorted = values.stream().sorted().toList();
        if (sorted.isEmpty()) return 0;
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index);
    }

    private record GoldenCase(String id, String category, String kind, String question) { }

    @FunctionalInterface
    private interface ToolBody<T> {
        AgentToolResult run(AgentToolContext context, T arguments);
    }
}
