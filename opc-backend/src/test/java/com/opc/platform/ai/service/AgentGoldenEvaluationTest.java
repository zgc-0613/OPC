package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderException;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.ai.tool.AgentTool;
import com.opc.platform.ai.tool.AgentToolContext;
import com.opc.platform.ai.tool.AgentToolException;
import com.opc.platform.ai.tool.AgentToolRegistry;
import com.opc.platform.ai.tool.AgentToolResult;
import com.opc.platform.ai.tool.CompareCasesArguments;
import com.opc.platform.ai.tool.GetSourceArguments;
import com.opc.platform.ai.tool.SearchCasesArguments;
import com.opc.platform.ai.tool.SearchPoliciesArguments;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.tag.service.IndustryTagService;
import com.opc.platform.tag.vo.IndustryResolution;
import com.opc.platform.userauth.AuthenticatedUser;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGoldenEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void everyFixtureDeclaresProviderToolStateAndCitationContracts() throws Exception {
        JsonNode fixtures = loadFixtures();
        assertTrue(fixtures.isArray());
        assertTrue(fixtures.size() >= 20);
        for (JsonNode fixture : fixtures) {
            String id = fixture.path("id").asText("missing-id");
            assertTrue(fixture.path("input").isObject(), id + " must declare input");
            assertTrue(fixture.path("providerRounds").isArray(), id + " must declare providerRounds");
            assertTrue(fixture.path("toolResults").isObject(), id + " must declare toolResults");
            assertTrue(fixture.path("expected").isObject(), id + " must declare expected");
            assertTrue(fixture.path("expected").path("stateTrace").isArray(), id + " must declare stateTrace");
            assertTrue(fixture.path("expected").path("tools").isArray(), id + " must declare tools");
            assertTrue(fixture.path("expected").path("allowedCitations").isArray(),
                    id + " must declare allowedCitations");
            assertTrue(fixture.path("expected").path("forbiddenCitations").isArray(),
                    id + " must declare forbiddenCitations");
            assertTrue(fixture.path("expected").path("claimEvidence").isObject(),
                    id + " must declare claimEvidence");
        }
    }

    @Test
    void fixtureDrivenEvaluationSeparatesRuntimeContractsFromModelQuality() throws Exception {
        JsonNode fixtures = loadFixtures();
        Set<String> coverage = new LinkedHashSet<>();
        int passed = 0;
        int expectedCompleted = 0;
        int completed = 0;
        int evidenceInsufficient = 0;
        int controlledFailures = 0;
        int totalRounds = 0;
        int totalTools = 0;
        int totalTokens = 0;
        List<Long> latencies = new ArrayList<>();

        for (JsonNode fixture : fixtures) {
            coverage.add(fixture.path("coverage").asText());
            EvaluationResult result = evaluate(fixture);
            passed++;
            if ("completed".equals(fixture.path("expected").path("status").asText())) expectedCompleted++;
            if (result.completed()) completed++;
            if (result.evidenceInsufficient()) evidenceInsufficient++;
            if (result.controlledFailure()) controlledFailures++;
            totalRounds += result.modelRounds();
            totalTools += result.toolCalls();
            totalTokens += result.totalTokens();
            if (result.latencyMs() >= 0) latencies.add(result.latencyMs());
        }

        assertEquals(fixtures.size(), passed);
        assertEquals(expectedCompleted, completed);
        assertTrue(coverage.containsAll(Set.of(
                "normal_retrieval", "missing_region", "clarification_convergence", "no_evidence",
                "unknown_tool", "unknown_parameter", "prompt_injection", "forbidden_id",
                "malicious_database_text", "duplicate_call", "cancel", "recovery", "provider_error"
        )));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("fixtureCount", fixtures.size());
        metrics.put("contractPassRate", ratio(passed, fixtures.size()));
        metrics.put("expectedCompletionRate", ratio(completed, expectedCompleted));
        metrics.put("evidenceInsufficientCount", evidenceInsufficient);
        metrics.put("controlledFailureCount", controlledFailures);
        metrics.put("acceptedUnknownCitationCount", 0);
        metrics.put("averageModelRounds", ratio(totalRounds, fixtures.size()));
        metrics.put("averageToolCalls", ratio(totalTools, fixtures.size()));
        metrics.put("averageTokens", ratio(totalTokens, fixtures.size()));
        metrics.put("p50LatencyMs", percentile(latencies, 0.50));
        metrics.put("p95LatencyMs", percentile(latencies, 0.95));
        metrics.put("scope", "deterministic_runtime_contract_only");
        System.out.println("AGENT_FIXTURE_CONTRACT_METRICS=" + objectMapper.writeValueAsString(metrics));
    }

    private EvaluationResult evaluate(JsonNode fixture) {
        return switch (fixture.path("executor").asText()) {
            case "clarification" -> evaluateClarification(fixture);
            case "clarification_then_orchestrator" -> evaluateClarificationThenOrchestrator(fixture);
            case "orchestrator" -> evaluateOrchestrator(fixture, fixture.path("input").path("profile"));
            case "cancel" -> evaluateCancellation(fixture);
            case "recovery" -> evaluateRecovery(fixture);
            default -> throw new AssertionError(fixture.path("id").asText() + " has unknown executor");
        };
    }

    private EvaluationResult evaluateClarification(JsonNode fixture) {
        String id = fixture.path("id").asText();
        AgentClarificationDecision decision = clarificationPolicy().evaluate(
                json(fixture.path("input").path("profile")),
                json(fixture.path("input").path("researchContext")),
                fixture.path("input").path("message").asText()
        );
        String status = decision.evidenceInsufficient()
                ? "evidence_insufficient"
                : decision.question() == null ? "planning" : "clarification_needed";
        assertEquals(fixture.path("expected").path("status").asText(), status, id);
        if ("clarification_needed".equals(status)) assertFalse(decision.question().isBlank(), id);
        if ("planning".equals(status)) assertNull(decision.question(), id);
        assertTrace(fixture, List.of("received", status));
        assertNoRuntimeOutputs(fixture);
        return new EvaluationResult(false, "evidence_insufficient".equals(status), false, 0, 0, 0, -1);
    }

    private EvaluationResult evaluateClarificationThenOrchestrator(JsonNode fixture) {
        AgentClarificationDecision decision = clarificationPolicy().evaluate(
                json(fixture.path("input").path("profile")),
                json(fixture.path("input").path("researchContext")),
                fixture.path("input").path("message").asText()
        );
        assertNull(decision.question(), fixture.path("id").asText());
        assertFalse(decision.evidenceInsufficient(), fixture.path("id").asText());
        JsonNode context;
        try {
            context = objectMapper.readTree(decision.contextJson()).path("resolvedFields");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        return evaluateOrchestrator(fixture, context);
    }

    private EvaluationResult evaluateOrchestrator(JsonNode fixture, JsonNode profile) {
        String id = fixture.path("id").asText();
        List<ToolInvocation> attemptedTools = new ArrayList<>();
        AgentToolRegistry registry = registry(fixture.path("toolResults"), attemptedTools);
        AgentOrchestrator orchestrator = new AgentOrchestrator(objectMapper, registry);
        ArrayDeque<JsonNode> providerRounds = new ArrayDeque<>();
        fixture.path("providerRounds").forEach(providerRounds::addLast);
        List<String> trace = new ArrayList<>();
        String expectedDiagnostic = fixture.path("expected").path("diagnosticCode").asText("");

        try {
            AgentOrchestratorOutcome outcome = orchestrator.execute(
                    new AgentOrchestratorInput(
                            100L, 42L, json(profile), fixture.path("input").path("message").asText(),
                            List.of(), config()
                    ),
                    request -> providerResponse(providerRounds, id),
                    progress -> trace.add(progress.stage())
            );
            trace.add(outcome.status());
            assertTrue(expectedDiagnostic.isBlank(), id + " unexpectedly completed");
            assertEquals(fixture.path("expected").path("status").asText(), outcome.status(), id);
            verifyCitations(fixture, outcome.citations());
            verifyTools(fixture, attemptedTools);
            assertTrace(fixture, trace);
            assertTrue(providerRounds.isEmpty(), id + " left unused provider rounds");
            return new EvaluationResult(
                    "completed".equals(outcome.status()),
                    "evidence_insufficient".equals(outcome.status()),
                    false,
                    outcome.modelRounds(), outcome.toolCallCount(), outcome.totalTokens(), outcome.latencyMs()
            );
        } catch (AgentToolException exception) {
            trace.add("failed");
            assertEquals(expectedDiagnostic, exception.getDiagnosticCode(), id);
        } catch (AgentOrchestratorException exception) {
            trace.add("failed");
            assertEquals(expectedDiagnostic, exception.getDiagnosticCode(), id);
        } catch (AiProviderException exception) {
            trace.add("failed");
            assertEquals(expectedDiagnostic, exception.getDiagnosticCode(), id);
        }
        assertEquals("failed", fixture.path("expected").path("status").asText(), id);
        verifyTools(fixture, attemptedTools);
        verifyCitations(fixture, List.of());
        assertTrace(fixture, trace);
        assertTrue(providerRounds.isEmpty(), id + " left unused provider rounds");
        return new EvaluationResult(false, false, true, 0, attemptedTools.size(), 0, -1);
    }

    private EvaluationResult evaluateCancellation(JsonNode fixture) {
        String id = fixture.path("id").asText();
        AiAnalysisRunMapper mapper = mock(AiAnalysisRunMapper.class);
        AiAnalysisRun received = run(601L, "received");
        AiAnalysisRun cancelled = run(601L, "cancelled");
        when(mapper.selectOwnedAgentRunForUpdate(601L, 42L)).thenReturn(received);
        when(mapper.selectOwnedAgentRun(601L, 42L)).thenReturn(cancelled);
        when(mapper.cancelOwnedAgentRun(anyLong(), anyLong(), any())).thenReturn(1);
        AgentRunLifecycleService lifecycle = new AgentRunLifecycleService(
                mapper, mock(AiClient.class), mock(AiRuntimeSettingsProvider.class));
        AiAnalysisRun result = lifecycle.cancel(
                new AuthenticatedUser(42L, "fixture-user", "fixture@example.com"), 601L);
        assertEquals(fixture.path("expected").path("status").asText(), result.getStatus(), id);
        assertTrace(fixture, List.of("received", result.getStatus()));
        assertNoRuntimeOutputs(fixture);
        return new EvaluationResult(false, false, false, 0, 0, 0, -1);
    }

    private EvaluationResult evaluateRecovery(JsonNode fixture) {
        String id = fixture.path("id").asText();
        AiAnalysisRunMapper mapper = mock(AiAnalysisRunMapper.class);
        AiAnalysisRun received = run(701L, "received");
        AiAnalysisRun running = run(701L, "running");
        running.setExecutionAttempts(1);
        when(mapper.selectClaimableAgentRunForUpdate(any(), anyInt())).thenReturn(received);
        when(mapper.claimAgentRun(anyLong(), any(), any(), any(), anyInt())).thenReturn(1);
        when(mapper.selectRunForUpdate(701L)).thenReturn(running);
        AiAnalysisRun claimed = new AgentRunQueueService(mapper).claimNext("fixture-worker");
        assertNotNull(claimed, id);
        assertEquals(fixture.path("expected").path("status").asText(), claimed.getStatus(), id);
        assertTrace(fixture, List.of("received", claimed.getStatus()));
        assertNoRuntimeOutputs(fixture);
        return new EvaluationResult(false, false, false, 0, 0, 0, -1);
    }

    private AgentClarificationPolicy clarificationPolicy() {
        Region hubei = new Region();
        hubei.setId(1L);
        hubei.setName("湖北省");
        Region hunan = new Region();
        hunan.setId(2L);
        hunan.setName("湖南省");
        RegionMapper regions = mock(RegionMapper.class);
        when(regions.selectList(any())).thenReturn(List.of(hubei, hunan));
        when(regions.selectById(1L)).thenReturn(hubei);
        IndustryTagService industries = mock(IndustryTagService.class);
        when(industries.resolve(any(), any(), anyBoolean())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            String text = invocation.getArgument(1);
            if (Long.valueOf(9L).equals(id) || (text != null && text.contains("人工智能"))) {
                return new IndustryResolution(9L, "人工智能", "industry", "fixture", 1.0, false);
            }
            return IndustryResolution.unresolved();
        });
        return new AgentClarificationPolicy(objectMapper, regions, industries);
    }

    private AgentToolRegistry registry(JsonNode toolResults, List<ToolInvocation> attemptedTools) {
        AiAgentToolCallMapper mapper = mock(AiAgentToolCallMapper.class);
        AtomicLong ids = new AtomicLong(1);
        when(mapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            AiAgentToolCall audit = invocation.getArgument(0);
            audit.setId(ids.getAndIncrement());
            try {
                attemptedTools.add(new ToolInvocation(
                        audit.getToolName(), objectMapper.readTree(audit.getArgumentsJson())));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            return 1;
        });
        when(mapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        return new AgentToolRegistry(
                List.of(
                        fixtureTool("search_cases", SearchCasesArguments.class, toolResults.path("search_cases")),
                        fixtureTool("search_policies", SearchPoliciesArguments.class, toolResults.path("search_policies")),
                        fixtureTool("get_source", GetSourceArguments.class, toolResults.path("get_source")),
                        fixtureTool("compare_cases", CompareCasesArguments.class, toolResults.path("compare_cases"))
                ),
                objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(),
                mapper
        );
    }

    private <T> AgentTool<T> fixtureTool(String name, Class<T> type, JsonNode configured) {
        return new AgentTool<>() {
            public String name() { return name; }
            public String description() { return "Deterministic fixture data for " + name; }
            public Class<T> argumentType() { return type; }
            public String argumentSchema() { return schema(name); }
            public AgentToolResult execute(AgentToolContext context, T arguments) {
                if (arguments instanceof GetSourceArguments source
                        && !context.allowedSourceIds().contains(source.getSourceId())) {
                    throw new AgentToolException("FORBIDDEN_SOURCE_ID", "source is outside this fixture run");
                }
                if (arguments instanceof CompareCasesArguments comparison
                        && !context.allowedCaseIds().containsAll(comparison.getCaseIds())) {
                    throw new AgentToolException("FORBIDDEN_CASE_ID", "case is outside this fixture run");
                }
                JsonNode output = configured.path("output").isMissingNode()
                        ? objectMapper.createObjectNode().putArray("items")
                        : configured.path("output").deepCopy();
                Set<Long> sources = longSet(configured.path("sourceIds"));
                Set<Long> cases = longSet(configured.path("caseIds"));
                return new AgentToolResult(
                        output,
                        configured.path("evidenceCount").asInt(sources.size() + cases.size()),
                        configured.path("evidenceHash").asText(
                                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                        sources,
                        cases
                );
            }
        };
    }

    private AiProviderResponse providerResponse(ArrayDeque<JsonNode> rounds, String id) {
        if (rounds.isEmpty()) fail(id + " requested an undeclared provider round");
        JsonNode round = rounds.removeFirst();
        if (round.hasNonNull("errorCode")) {
            throw new AiProviderException(round.path("errorCode").asText(), "deterministic fixture failure");
        }
        int prompt = round.path("promptTokens").asInt(10);
        int completion = round.path("completionTokens").asInt(5);
        int total = round.path("totalTokens").asInt(prompt + completion);
        return new AiProviderResponse(
                round.path("content").asText(), prompt, completion, total,
                round.path("latencyMs").asLong(20), round.path("requestId").asText("fixture-request"),
                round.path("finishReason").asText("stop")
        );
    }

    private void verifyTools(JsonNode fixture, List<ToolInvocation> actual) {
        JsonNode expected = fixture.path("expected").path("tools");
        assertEquals(expected.size(), actual.size(), fixture.path("id").asText() + " tool count");
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index).path("name").asText(), actual.get(index).name(),
                    fixture.path("id").asText() + " tool name " + index);
            assertEquals(expectedAuditedArguments(expected.get(index).path("arguments")),
                    actual.get(index).arguments(),
                    fixture.path("id").asText() + " tool arguments " + index);
        }
    }

    private JsonNode expectedAuditedArguments(JsonNode arguments) {
        var safe = objectMapper.createObjectNode();
        for (String field : List.of(
                "scope", "limit", "regionId", "industryTagId", "sourceId", "caseIds", "dimensions")) {
            if (arguments.has(field)) safe.set(field, arguments.path(field).deepCopy());
        }
        safe.put("queryPresent", arguments.path("query").isTextual()
                && !arguments.path("query").asText().isBlank());
        safe.put("categoryPresent", arguments.path("category").isTextual()
                && !arguments.path("category").asText().isBlank());
        safe.put("industryPresent", arguments.path("industry").isTextual()
                && !arguments.path("industry").asText().isBlank());
        return safe;
    }

    private void verifyCitations(JsonNode fixture, List<AgentCitation> citations) {
        String id = fixture.path("id").asText();
        Set<Long> allowed = longSet(fixture.path("expected").path("allowedCitations"));
        Set<Long> forbidden = longSet(fixture.path("expected").path("forbiddenCitations"));
        JsonNode claimEvidence = fixture.path("expected").path("claimEvidence");
        for (AgentCitation citation : citations) {
            assertTrue(allowed.contains(citation.sourceId()), id + " accepted unauthorized citation");
            assertFalse(forbidden.contains(citation.sourceId()), id + " accepted forbidden citation");
            JsonNode supporting = claimEvidence.path(citation.claim());
            assertTrue(supporting.isArray(), id + " has no explicit evidence mapping for claim");
            assertTrue(longSet(supporting).contains(citation.sourceId()), id + " citation does not support claim");
        }
        if ("completed".equals(fixture.path("expected").path("status").asText())) {
            assertFalse(citations.isEmpty(), id + " completed without citations");
        } else {
            assertTrue(citations.isEmpty(), id + " non-completion accepted citations");
        }
    }

    private void assertTrace(JsonNode fixture, List<String> actual) {
        List<String> expected = new ArrayList<>();
        fixture.path("expected").path("stateTrace").forEach(node -> expected.add(node.asText()));
        if ("clarification_then_orchestrator".equals(fixture.path("executor").asText())) {
            actual = new ArrayList<>(actual);
            actual.add(0, "planning");
            actual.add(0, "received");
        }
        assertEquals(expected, actual, fixture.path("id").asText() + " state trace");
    }

    private void assertNoRuntimeOutputs(JsonNode fixture) {
        assertEquals(0, fixture.path("expected").path("tools").size(), fixture.path("id").asText());
        assertEquals(0, fixture.path("expected").path("allowedCitations").size(), fixture.path("id").asText());
        assertEquals(0, fixture.path("expected").path("forbiddenCitations").size(), fixture.path("id").asText());
    }

    private AiAnalysisRun run(Long id, String status) {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(id);
        run.setUserId(42L);
        run.setStatus(status);
        return run;
    }

    private JsonNode loadFixtures() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/ai/agent-golden-evaluation.json")) {
            assertNotNull(stream);
            return objectMapper.readTree(stream);
        }
    }

    private AgentRuntimeConfig config() {
        return new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan");
    }

    private Set<Long> longSet(JsonNode values) {
        Set<Long> result = new LinkedHashSet<>();
        if (values != null && values.isArray()) values.forEach(value -> result.add(value.asLong()));
        return result;
    }

    private String json(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "{}" : node.toString();
    }

    private String schema(String name) {
        return switch (name) {
            case "search_cases" -> """
                    {"type":"object","additionalProperties":false,"properties":{"regionId":{"type":"integer"},"industryTagId":{"type":"integer"},"industry":{"type":"string","maxLength":100},"query":{"type":"string","maxLength":120},"category":{"type":"string","maxLength":50},"limit":{"type":"integer","minimum":1,"maximum":10}}}
                    """;
            case "search_policies" -> """
                    {"type":"object","additionalProperties":false,"required":["regionId"],"properties":{"regionId":{"type":"integer"},"industryTagId":{"type":"integer"},"industry":{"type":"string","maxLength":100},"query":{"type":"string","maxLength":120},"limit":{"type":"integer","minimum":1,"maximum":10}}}
                    """;
            case "get_source" -> """
                    {"type":"object","additionalProperties":false,"required":["sourceId"],"properties":{"sourceId":{"type":"integer"}}}
                    """;
            default -> """
                    {"type":"object","additionalProperties":false,"required":["caseIds"],"properties":{"caseIds":{"type":"array","minItems":2,"maxItems":3,"items":{"type":"integer"}},"dimensions":{"type":"array","maxItems":6,"items":{"type":"string","enum":["businessModel","technicalPath","targetCustomer","outcome","regionalContext","evidenceStrength"]}}}}
                    """;
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

    private record ToolInvocation(String name, JsonNode arguments) { }

    private record EvaluationResult(
            boolean completed,
            boolean evidenceInsufficient,
            boolean controlledFailure,
            int modelRounds,
            int toolCalls,
            int totalTokens,
            long latencyMs
    ) { }
}
