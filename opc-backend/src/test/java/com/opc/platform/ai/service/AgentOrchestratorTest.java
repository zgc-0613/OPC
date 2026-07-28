package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.mapper.AgentEvidenceToolMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.tool.AgentTool;
import com.opc.platform.ai.tool.AgentToolContext;
import com.opc.platform.ai.tool.AgentToolRegistry;
import com.opc.platform.ai.tool.AgentToolResult;
import com.opc.platform.ai.tool.CompareCasesArguments;
import com.opc.platform.ai.tool.CompareCasesTool;
import com.opc.platform.ai.tool.GetSourceArguments;
import com.opc.platform.ai.tool.GetSourceTool;
import com.opc.platform.ai.tool.SearchCasesArguments;
import com.opc.platform.ai.tool.SearchCasesTool;
import com.opc.platform.ai.tool.SearchPoliciesTool;
import com.opc.platform.ai.tool.SearchPoliciesArguments;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.mapper.SourceMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {

    @Test
    void searchResultsDriveCompareCasesInNextModelRound() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        AtomicInteger auditId = new AtomicInteger();
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId((long) auditId.incrementAndGet());
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);

        AtomicInteger generatedId = new AtomicInteger(700);
        AtomicReference<List<Long>> returnedCaseIds = new AtomicReference<>();
        AtomicReference<CompareCasesArguments> compared = new AtomicReference<>();
        AgentTool<SearchCasesArguments> search = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "search verified cases"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                List<Long> caseIds = returnedCaseIds.updateAndGet(existing -> existing == null
                        ? List.of((long) generatedId.incrementAndGet(), (long) generatedId.incrementAndGet())
                        : existing);
                var output = objectMapper.createObjectNode();
                var items = output.putArray("items");
                for (int index = 0; index < caseIds.size(); index++) {
                    items.addObject()
                            .put("caseId", caseIds.get(index))
                            .put("sourceId", 801L + index)
                            .put("title", "Dynamic case " + index);
                }
                return new AgentToolResult(output, 2, "a".repeat(64),
                        Set.of(801L, 802L), Set.copyOf(caseIds));
            }
        };
        AgentTool<CompareCasesArguments> compare = new AgentTool<>() {
            public String name() { return "compare_cases"; }
            public String description() { return "compare authorized cases"; }
            public Class<CompareCasesArguments> argumentType() { return CompareCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"caseIds\"],"
                        + "\"properties\":{\"caseIds\":{\"type\":\"array\",\"minItems\":2,\"maxItems\":3,"
                        + "\"items\":{\"type\":\"integer\"}},\"dimensions\":{\"type\":\"array\"}}}";
            }
            public AgentToolResult execute(AgentToolContext context, CompareCasesArguments arguments) {
                compared.set(arguments);
                assertEquals(returnedCaseIds.get(), arguments.getCaseIds());
                assertTrue(context.allowedCaseIds().containsAll(arguments.getCaseIds()));
                return new AgentToolResult(objectMapper.valueToTree(java.util.Map.of(
                        "cases", List.of(
                                java.util.Map.of("caseId", arguments.getCaseIds().get(0), "sourceId", 801L),
                                java.util.Map.of("caseId", arguments.getCaseIds().get(1), "sourceId", 802L)
                        )
                )), 2, "b".repeat(64), Set.of(801L, 802L), Set.copyOf(arguments.getCaseIds()));
            }
        };
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(search, compare), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), callMapper)
        );
        AtomicInteger round = new AtomicInteger();

        AgentOrchestratorOutcome outcome = orchestrator.execute(input(config()), request -> {
            return switch (round.incrementAndGet()) {
                case 1 -> new AiProviderResponse("""
                        {"action":"plan","intent":"case_comparison","researchQuestions":["compare local cases"],
                         "toolRequests":[{"requestId":"cases","toolName":"search_cases","arguments":{},"dependsOn":[]}],
                         "comparisonDimensions":["businessModel"],
                         "outputSections":["directAnswer","comparison","recommendations","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop");
                case 2 -> {
                    String evidence = request.messages().get(request.messages().size() - 1).content();
                    assertTrue(evidence.contains(String.valueOf(returnedCaseIds.get().get(0))));
                    assertTrue(evidence.contains(String.valueOf(returnedCaseIds.get().get(1))));
                    yield new AiProviderResponse("""
                            {"action":"continue","toolRequests":[
                              {"requestId":"comparison","toolName":"compare_cases","arguments":{"caseIds":[%d,%d],"dimensions":["businessModel"]},"dependsOn":["cases"]}
                            ]}
                            """.formatted(returnedCaseIds.get().get(0), returnedCaseIds.get().get(1)),
                            10, 5, 15, 10, "continue", "stop");
                }
                case 3 -> new AiProviderResponse("""
                        {"action":"final","answer":"The verified cases support a comparison.",
                         "citations":[{"sourceId":801,"claim":"First case"},{"sourceId":802,"claim":"Second case"}],
                         "confidence":0.8}
                        """, 10, 5, 15, 10, "final", "stop");
                default -> throw new AssertionError("unexpected model round");
            };
        }, progress -> { });

        assertEquals("completed", outcome.status());
        assertEquals(3, outcome.modelRounds());
        assertEquals(2, outcome.toolCallCount());
        assertEquals(returnedCaseIds.get(), compared.get().getCaseIds());
    }

    @Test
    void explicitSourceVerificationRequirementCannotBeLoweredByModelIntent() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        AtomicInteger auditId = new AtomicInteger();
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId((long) auditId.incrementAndGet());
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        AtomicReference<Long> returnedSourceId = new AtomicReference<>();
        AtomicReference<GetSourceArguments> loaded = new AtomicReference<>();
        AgentTool<SearchPoliciesArguments> search = new AgentTool<>() {
            public String name() { return "search_policies"; }
            public String description() { return "search verified policies"; }
            public Class<SearchPoliciesArguments> argumentType() { return SearchPoliciesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchPoliciesArguments arguments) {
                Long sourceId = returnedSourceId.updateAndGet(existing -> existing == null
                        ? 900L + Math.abs(System.identityHashCode(this) % 100) : existing);
                var output = objectMapper.createObjectNode();
                output.putArray("items").addObject()
                        .put("policyId", sourceId + 1000)
                        .put("sourceId", sourceId)
                        .put("title", "Dynamic policy");
                return new AgentToolResult(output, 1, "c".repeat(64), Set.of(sourceId), Set.of());
            }
        };
        AgentTool<GetSourceArguments> getSource = new AgentTool<>() {
            public String name() { return "get_source"; }
            public String description() { return "load an authorized source"; }
            public Class<GetSourceArguments> argumentType() { return GetSourceArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"sourceId\"],"
                        + "\"properties\":{\"sourceId\":{\"type\":\"integer\"}}}";
            }
            public AgentToolResult execute(AgentToolContext context, GetSourceArguments arguments) {
                loaded.set(arguments);
                assertEquals(returnedSourceId.get(), arguments.getSourceId());
                assertTrue(context.allowedSourceIds().contains(arguments.getSourceId()));
                return new AgentToolResult(objectMapper.valueToTree(java.util.Map.of(
                        "sourceId", arguments.getSourceId(), "title", "Verified source"
                )), 1, "d".repeat(64), Set.of(arguments.getSourceId()), Set.of());
            }
        };
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(search, getSource), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), callMapper)
        );
        AtomicInteger round = new AtomicInteger();
        AgentOrchestratorOutcome outcome = orchestrator.execute(new AgentOrchestratorInput(
                91L, 42L, "{\"regionId\":2,\"industry\":\"AI\"}",
                "请核验这项政策的原始来源和证据链", List.of(), config()),
                request -> switch (round.incrementAndGet()) {
            case 1 -> new AiProviderResponse("""
                    {"action":"plan","intent":"policy_lookup","researchQuestions":["verify policy source"],
                     "toolRequests":[{"requestId":"policies","toolName":"search_policies","arguments":{},"dependsOn":[]}],
                     "comparisonDimensions":[],"outputSections":["directAnswer","policyInsights","citations"]}
                    """, 10, 5, 15, 10, "plan", "stop");
            case 2 -> new AiProviderResponse("""
                    {"action":"final","answer":"The source was verified.",
                     "citations":[{"sourceId":%d,"claim":"Verified source"}],"confidence":0.8}
                    """.formatted(returnedSourceId.get()), 10, 5, 15, 10, "final", "stop");
            default -> throw new AssertionError("unexpected model round");
        }, progress -> { });

        assertEquals("completed", outcome.status());
        assertEquals(2, outcome.modelRounds());
        assertEquals(2, outcome.toolCallCount());
        assertEquals(returnedSourceId.get(), loaded.get().getSourceId());
    }

    @Test
    void invalidContinuationDependencyGetsOneBoundedRecoveryBeforeToolExecution() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        AtomicInteger auditId = new AtomicInteger();
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId((long) auditId.incrementAndGet());
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        AgentTool<SearchPoliciesArguments> search = fixtureTool(
                "search_policies", SearchPoliciesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}",
                objectMapper, 901L, null
        );
        AtomicInteger sourceExecutions = new AtomicInteger();
        AgentTool<GetSourceArguments> source = new AgentTool<>() {
            public String name() { return "get_source"; }
            public String description() { return "load authorized source"; }
            public Class<GetSourceArguments> argumentType() { return GetSourceArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"sourceId\"],"
                        + "\"properties\":{\"sourceId\":{\"type\":\"integer\"}}}";
            }
            public AgentToolResult execute(AgentToolContext context, GetSourceArguments arguments) {
                sourceExecutions.incrementAndGet();
                assertEquals(901L, arguments.getSourceId());
                assertTrue(context.allowedSourceIds().contains(901L));
                return new AgentToolResult(
                        objectMapper.valueToTree(java.util.Map.of(
                                "sourceId", 901L, "title", "Verified source")),
                        1, "e".repeat(64), Set.of(901L), Set.of());
            }
        };
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(search, source), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), callMapper)
        );
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();
        AtomicInteger round = new AtomicInteger();

        AgentOrchestratorOutcome outcome = orchestrator.execute(new AgentOrchestratorInput(
                91L, 42L, "{\"regionId\":1,\"industry\":\"AI\"}",
                "Research local AI opportunities", List.of(), null, config(), "general_research"), request -> {
            requests.add(request);
            return switch (round.incrementAndGet()) {
                case 1 -> new AiProviderResponse("""
                        {"action":"plan","intent":"source_verification","researchQuestions":["verify source"],
                         "toolRequests":[{"requestId":"policies","toolName":"search_policies","arguments":{},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","policyInsights","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop");
                case 2 -> new AiProviderResponse("""
                        {"action":"continue","toolRequests":[
                          {"requestId":"source","toolName":"get_source","arguments":{"sourceId":901},"dependsOn":["invented-request"]}
                        ]}
                        """, 10, 5, 15, 10, "invalid-continuation", "stop");
                case 3 -> new AiProviderResponse("""
                        {"action":"continue","toolRequests":[
                          {"requestId":"source","toolName":"get_source","arguments":{"sourceId":901},"dependsOn":["policies"]}
                        ]}
                        """, 10, 5, 15, 10, "recovered-continuation", "stop");
                case 4 -> {
                    assertEquals(1, sourceExecutions.get());
                    yield new AiProviderResponse("""
                            {"action":"final","answer":"The source was verified.",
                             "citations":[{"sourceId":901,"claim":"Verified source"}],"confidence":0.8}
                            """, 10, 5, 15, 10, "final", "stop");
                }
                default -> throw new AssertionError("unexpected model round");
            };
        }, progress -> { });

        assertEquals("completed", outcome.status());
        assertEquals(4, outcome.modelRounds());
        assertEquals(2, outcome.toolCallCount());
        assertEquals(2, sourceExecutions.get());
        String recovery = requests.get(2).messages().get(requests.get(2).messages().size() - 1).content();
        assertTrue(recovery.contains("policies"));
        assertTrue(recovery.contains("901"));
        assertFalse(requests.get(2).messages().stream().anyMatch(message ->
                message.content() != null && message.content().contains("invented-request")));
    }

    @Test
    void explicitComparisonRequirementCannotBeLoweredByModelIntent() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = guardedCallMapper();
        AgentTool<SearchCasesArguments> search = fixtureTool(
                "search_cases", SearchCasesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}",
                objectMapper, 801L, 701L
        );
        AtomicInteger compareExecutions = new AtomicInteger();
        AgentTool<CompareCasesArguments> compare = new AgentTool<>() {
            public String name() { return "compare_cases"; }
            public String description() { return "compare authorized cases"; }
            public Class<CompareCasesArguments> argumentType() { return CompareCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"caseIds\"],"
                        + "\"properties\":{\"caseIds\":{\"type\":\"array\",\"minItems\":2,\"maxItems\":3,"
                        + "\"items\":{\"type\":\"integer\"}},\"dimensions\":{\"type\":\"array\"}}}";
            }
            public AgentToolResult execute(AgentToolContext context, CompareCasesArguments arguments) {
                compareExecutions.incrementAndGet();
                return new AgentToolResult(
                        objectMapper.valueToTree(java.util.Map.of("cases", List.of(
                                java.util.Map.of("caseId", 701L, "sourceId", 801L),
                                java.util.Map.of("caseId", 702L, "sourceId", 802L)
                        ))), 2, "b".repeat(64), Set.of(801L, 802L), Set.of(701L, 702L));
            }
        };
        AgentTool<SearchCasesArguments> twoCaseSearch = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "search two cases"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() { return search.argumentSchema(); }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                var output = objectMapper.createObjectNode();
                output.putArray("items")
                        .addObject().put("caseId", 701L).put("sourceId", 801L).put("title", "Case A");
                output.withArray("items")
                        .addObject().put("caseId", 702L).put("sourceId", 802L).put("title", "Case B");
                return new AgentToolResult(output, 2, "a".repeat(64),
                        Set.of(801L, 802L), Set.of(701L, 702L));
            }
        };
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(twoCaseSearch, compare), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), callMapper)
        );
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();
        AtomicInteger round = new AtomicInteger();

        AgentOrchestratorOutcome outcome = orchestrator.execute(new AgentOrchestratorInput(
                91L, 42L, "{\"regionId\":2,\"industry\":\"AI\"}",
                "请比较两个已核验的创业案例并说明差异", List.of(), config()), request -> {
            requests.add(request);
            return switch (round.incrementAndGet()) {
                case 1 -> new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis","researchQuestions":["compare cases"],
                         "toolRequests":[{"requestId":"cases","toolName":"search_cases","arguments":{},"dependsOn":[]}],
                         "comparisonDimensions":["businessModel"],
                         "outputSections":["directAnswer","comparison","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop");
                case 2 -> new AiProviderResponse("""
                        {"action":"final","answer":"The two authorized cases were compared.",
                         "citations":[{"sourceId":801,"claim":"Case A"},{"sourceId":802,"claim":"Case B"}],"confidence":0.8}
                        """, 10, 5, 15, 10, "final", "stop");
                default -> throw new AssertionError("unexpected model round");
            };
        }, progress -> { });

        assertEquals("completed", outcome.status());
        assertEquals(2, outcome.modelRounds());
        assertEquals(2, outcome.toolCallCount());
        assertTrue(compareExecutions.get() >= 1);
    }

    @Test
    void comparisonCompletesServerOwnedChainAfterZeroResultSearch() {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger searchExecutions = new AtomicInteger();
        List<SearchCasesArguments> searchArguments = new ArrayList<>();
        AgentTool<SearchCasesArguments> search = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "search cases"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                searchArguments.add(arguments);
                int attempt = searchExecutions.incrementAndGet();
                var output = objectMapper.createObjectNode();
                output.putArray("items");
                Set<Long> sourceIds = new java.util.LinkedHashSet<>();
                Set<Long> caseIds = new java.util.LinkedHashSet<>();
                if (attempt > 1) {
                    output.withArray("items")
                            .addObject().put("caseId", 701L).put("sourceId", 801L).put("title", "Case A");
                    output.withArray("items")
                            .addObject().put("caseId", 702L).put("sourceId", 802L).put("title", "Case B");
                    sourceIds.add(801L);
                    sourceIds.add(802L);
                    caseIds.add(701L);
                    caseIds.add(702L);
                }
                return new AgentToolResult(
                        output, caseIds.size(), "a".repeat(64), sourceIds, caseIds);
            }
        };
        AtomicInteger compareExecutions = new AtomicInteger();
        AgentTool<CompareCasesArguments> compare = new AgentTool<>() {
            public String name() { return "compare_cases"; }
            public String description() { return "compare authorized cases"; }
            public Class<CompareCasesArguments> argumentType() { return CompareCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"caseIds\"],"
                        + "\"properties\":{\"caseIds\":{\"type\":\"array\",\"minItems\":2,\"maxItems\":3,"
                        + "\"items\":{\"type\":\"integer\"}},\"dimensions\":{\"type\":\"array\"}}}";
            }
            public AgentToolResult execute(AgentToolContext context, CompareCasesArguments arguments) {
                compareExecutions.incrementAndGet();
                return new AgentToolResult(
                        objectMapper.valueToTree(java.util.Map.of("cases", List.of(
                                java.util.Map.of("caseId", 701L, "sourceId", 801L),
                                java.util.Map.of("caseId", 702L, "sourceId", 802L)
                        ))), 2, "b".repeat(64), Set.of(801L, 802L), Set.of(701L, 702L));
            }
        };
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(search, compare), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), guardedCallMapper())
        );
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis","researchQuestions":["compare cases"],
                         "toolRequests":[{"requestId":"casesNarrow","toolName":"search_cases","arguments":{
                           "scope":"cross_region_reference","query":"an overly narrow generated phrase","category":"wrong","limit":1
                         },"dependsOn":[]}],
                         "comparisonDimensions":["businessModel"],
                         "outputSections":["directAnswer","comparison","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop"),
                new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"The cases were compared.\",\"citations\":[{\"sourceId\":801,\"claim\":\"Case A\"},{\"sourceId\":802,\"claim\":\"Case B\"}],\"confidence\":0.8}",
                        10, 5, 15, 10, "final", "stop")
        ));

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        91L, 42L, "{\"regionId\":2,\"industry\":\"AI\"}",
                        "请比较两个创业案例", List.of(), null,
                        new AgentRuntimeConfig(true, 5, 6, 28000, 12,
                                Duration.ofSeconds(120), "json_plan"),
                        "case_comparison"),
                request -> responses.removeFirst(), progress -> { }
        );

        assertEquals("completed", outcome.status());
        assertEquals(2, outcome.modelRounds());
        assertEquals(3, outcome.toolCallCount());
        assertTrue(searchExecutions.get() >= 2);
        assertTrue(compareExecutions.get() >= 1);
        assertEquals("selected", searchArguments.get(0).getScope());
        assertEquals(null, searchArguments.get(0).getQuery());
        assertEquals(null, searchArguments.get(0).getCategory());
        assertTrue(searchArguments.get(0).getLimit() >= 2);
    }

    @Test
    void comparisonCombinesOneSelectedAndOneCrossRegionCaseBeforeCompare() {
        ComparisonScenario scenario = runServerOwnedComparisonScenario(
                List.of(701L), List.of(702L), true);

        assertEquals("completed", scenario.outcome().status());
        assertEquals(3, scenario.outcome().toolCallCount());
        assertEquals(List.of(701L, 702L), scenario.comparedCaseIds());
    }

    @Test
    void comparisonDoesNotCountTheSameCaseTwiceAcrossSearches() {
        ComparisonScenario scenario = runServerOwnedComparisonScenario(
                List.of(701L), List.of(701L), false);

        assertEquals("evidence_insufficient", scenario.outcome().status());
        assertEquals(2, scenario.outcome().toolCallCount());
        assertTrue(scenario.comparedCaseIds().isEmpty());
    }

    @Test
    void comparisonStopsAfterOneBroaderSearchWhenBothSearchesReturnNoCases() {
        ComparisonScenario scenario = runServerOwnedComparisonScenario(
                List.of(), List.of(), false);

        assertEquals("evidence_insufficient", scenario.outcome().status());
        assertEquals(2, scenario.outcome().toolCallCount());
        assertTrue(scenario.comparedCaseIds().isEmpty());
    }

    @Test
    void missingComparisonToolsAreCompletedAfterAnUnrelatedModelSearch() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool<SearchPoliciesArguments> policies = fixtureTool(
                "search_policies", SearchPoliciesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}",
                objectMapper, 901L, null);
        AgentTool<SearchCasesArguments> cases = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "required cases"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                var output = objectMapper.createObjectNode();
                output.putArray("items").addObject().put("caseId", 701L).put("sourceId", 801L);
                output.withArray("items").addObject().put("caseId", 702L).put("sourceId", 802L);
                return new AgentToolResult(
                        output, 2, "a".repeat(64), Set.of(801L, 802L), Set.of(701L, 702L));
            }
        };
        AtomicReference<List<Long>> compared = new AtomicReference<>(List.of());
        AgentTool<CompareCasesArguments> compare = new AgentTool<>() {
            public String name() { return "compare_cases"; }
            public String description() { return "required comparison"; }
            public Class<CompareCasesArguments> argumentType() { return CompareCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"caseIds\"],"
                        + "\"properties\":{\"caseIds\":{\"type\":\"array\",\"minItems\":2,\"maxItems\":3,"
                        + "\"items\":{\"type\":\"integer\"}},\"dimensions\":{\"type\":\"array\"}}}";
            }
            public AgentToolResult execute(AgentToolContext context, CompareCasesArguments arguments) {
                assertEquals(List.of("businessModel", "outcome"), arguments.getDimensions());
                compared.set(List.copyOf(arguments.getCaseIds()));
                return new AgentToolResult(
                        objectMapper.valueToTree(java.util.Map.of("cases", List.of(
                                java.util.Map.of("caseId", 701L, "sourceId", 801L),
                                java.util.Map.of("caseId", 702L, "sourceId", 802L)))),
                        2, "b".repeat(64), Set.of(801L, 802L), Set.of(701L, 702L));
            }
        };
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper, new AgentToolRegistry(
                List.of(policies, cases, compare), objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), guardedCallMapper()));
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("""
                        {"action":"plan","intent":"policy_lookup","researchQuestions":["context"],
                         "toolRequests":[{"requestId":"policies","toolName":"search_policies","arguments":{},"dependsOn":[]}],
                         "comparisonDimensions":["business viability"],
                         "outputSections":["directAnswer","comparison","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop"),
                new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Compared.\",\"citations\":["
                                + "{\"sourceId\":801,\"claim\":\"Case A\"},"
                                + "{\"sourceId\":802,\"claim\":\"Case B\"}],\"confidence\":0.8}",
                        10, 5, 15, 10, "final", "stop")));

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        91L, 42L, "{\"regionId\":2,\"industry\":\"AI\"}",
                        "Compare two cases", List.of(), null,
                        new AgentRuntimeConfig(true, 5, 6, 28000, 12,
                                Duration.ofSeconds(120), "json_plan"),
                        "case_comparison"),
                request -> responses.removeFirst(), progress -> { });

        assertEquals("completed", outcome.status());
        assertEquals(3, outcome.toolCallCount());
        assertEquals(List.of(701L, 702L), compared.get());
    }

    @Test
    void missingPolicySearchIsCompletedAfterAnUnrelatedModelSearch() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool<SearchCasesArguments> cases = fixtureTool(
                "search_cases", SearchCasesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}",
                objectMapper, 801L, 701L);
        AtomicInteger policySearches = new AtomicInteger();
        AgentTool<SearchPoliciesArguments> policies = new AgentTool<>() {
            public String name() { return "search_policies"; }
            public String description() { return "required policy search"; }
            public Class<SearchPoliciesArguments> argumentType() { return SearchPoliciesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchPoliciesArguments arguments) {
                policySearches.incrementAndGet();
                var output = objectMapper.createObjectNode();
                output.putArray("items").addObject().put("policyId", 21L).put("sourceId", 901L);
                return new AgentToolResult(output, 1, "p".repeat(64), Set.of(901L), Set.of());
            }
        };
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper, new AgentToolRegistry(
                List.of(cases, policies), objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), guardedCallMapper()));
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis","researchQuestions":["context"],
                         "toolRequests":[{"requestId":"cases","toolName":"search_cases","arguments":{},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop"),
                new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Policy found.\",\"citations\":["
                                + "{\"sourceId\":901,\"claim\":\"Policy\"}],\"confidence\":0.8}",
                        10, 5, 15, 10, "final", "stop")));

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        91L, 42L, "{\"regionId\":2,\"industry\":\"AI\"}",
                        "Find policy", List.of(), null,
                        new AgentRuntimeConfig(true, 5, 4, 28000, 12,
                                Duration.ofSeconds(120), "json_plan"),
                        "policy_lookup"),
                request -> responses.removeFirst(), progress -> { });

        assertEquals("completed", outcome.status());
        assertEquals(2, outcome.toolCallCount());
        assertTrue(policySearches.get() >= 1);
    }

    @Test
    void optionalPlanSearchesCannotExhaustRequiredComparisonToolBudget() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool<SearchCasesArguments> search = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "search cases"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                var output = objectMapper.createObjectNode();
                output.putArray("items").addObject().put("caseId", 701L).put("sourceId", 801L);
                output.withArray("items").addObject().put("caseId", 702L).put("sourceId", 802L);
                return new AgentToolResult(
                        output, 2, "s".repeat(64), Set.of(801L, 802L), Set.of(701L, 702L));
            }
        };
        AtomicInteger compareExecutions = new AtomicInteger();
        AgentTool<CompareCasesArguments> compare = new AgentTool<>() {
            public String name() { return "compare_cases"; }
            public String description() { return "compare cases"; }
            public Class<CompareCasesArguments> argumentType() { return CompareCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"caseIds\"],"
                        + "\"properties\":{\"caseIds\":{\"type\":\"array\",\"minItems\":2,\"maxItems\":3,"
                        + "\"items\":{\"type\":\"integer\"}},\"dimensions\":{\"type\":\"array\"}}}";
            }
            public AgentToolResult execute(AgentToolContext context, CompareCasesArguments arguments) {
                compareExecutions.incrementAndGet();
                return new AgentToolResult(
                        objectMapper.valueToTree(java.util.Map.of("cases", arguments.getCaseIds())),
                        2, "c".repeat(64), Set.of(801L, 802L), Set.copyOf(arguments.getCaseIds()));
            }
        };
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(search, compare), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), guardedCallMapper())
        );
        AtomicInteger round = new AtomicInteger();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        91L, 42L, "{\"regionId\":2,\"industry\":\"AI\"}",
                        "Compare two verified cases", List.of(), null,
                        new AgentRuntimeConfig(true, 5, 4, 28000, 12,
                                Duration.ofSeconds(120), "json_plan"),
                        "case_comparison"),
                request -> switch (round.incrementAndGet()) {
                    case 1 -> new AiProviderResponse("""
                            {"action":"plan","intent":"case_analysis","researchQuestions":["compare cases"],
                             "toolRequests":[
                               {"requestId":"s1","toolName":"search_cases","arguments":{},"dependsOn":[]},
                               {"requestId":"s2","toolName":"search_cases","arguments":{},"dependsOn":[]},
                               {"requestId":"s3","toolName":"search_cases","arguments":{},"dependsOn":[]},
                               {"requestId":"s4","toolName":"search_cases","arguments":{},"dependsOn":[]}
                             ],"comparisonDimensions":["businessModel"],
                             "outputSections":["directAnswer","comparison","citations"]}
                            """, 10, 5, 15, 10, "plan", "stop");
                    case 2 -> new AiProviderResponse(
                            "{\"action\":\"final\",\"answer\":\"Compared.\",\"citations\":["
                                    + "{\"sourceId\":801,\"claim\":\"Case A\"},"
                                    + "{\"sourceId\":802,\"claim\":\"Case B\"}],\"confidence\":0.8}",
                            10, 5, 15, 10, "final", "stop");
                    default -> throw new AssertionError("unexpected model round");
                }, progress -> { }
        );

        assertEquals("completed", outcome.status());
        assertTrue(outcome.toolCallCount() <= 4);
        assertTrue(compareExecutions.get() >= 1);
    }

    @Test
    void dependentToolCannotBorrowIdsFromAnotherCompletedRequest() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = guardedCallMapper();
        AgentTool<SearchCasesArguments> search = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "search request-scoped cases"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,"
                        + "\"properties\":{\"query\":{\"type\":\"string\"}}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                boolean alpha = "alpha".equals(arguments.getQuery());
                long firstCase = alpha ? 701L : 801L;
                long firstSource = alpha ? 1701L : 1801L;
                var output = objectMapper.createObjectNode();
                output.putArray("items")
                        .addObject().put("caseId", firstCase).put("sourceId", firstSource).put("title", "Case 1");
                output.withArray("items")
                        .addObject().put("caseId", firstCase + 1).put("sourceId", firstSource + 1).put("title", "Case 2");
                return new AgentToolResult(output, 2, (alpha ? "a" : "b").repeat(64),
                        Set.of(firstSource, firstSource + 1), Set.of(firstCase, firstCase + 1));
            }
        };
        AtomicInteger compareExecutions = new AtomicInteger();
        AgentTool<CompareCasesArguments> compare = new AgentTool<>() {
            public String name() { return "compare_cases"; }
            public String description() { return "compare request-scoped cases"; }
            public Class<CompareCasesArguments> argumentType() { return CompareCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"caseIds\"],"
                        + "\"properties\":{\"caseIds\":{\"type\":\"array\",\"minItems\":2,\"maxItems\":3,"
                        + "\"items\":{\"type\":\"integer\"}},\"dimensions\":{\"type\":\"array\"}}}";
            }
            public AgentToolResult execute(AgentToolContext context, CompareCasesArguments arguments) {
                compareExecutions.incrementAndGet();
                return new AgentToolResult(
                        objectMapper.valueToTree(java.util.Map.of("cases", arguments.getCaseIds())),
                        2, "c".repeat(64), Set.of(1701L, 1702L), Set.copyOf(arguments.getCaseIds()));
            }
        };
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(search, compare), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), callMapper)
        );
        AtomicInteger round = new AtomicInteger();
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(new AgentOrchestratorInput(
                91L, 42L, "{\"regionId\":1,\"industry\":\"AI\"}",
                "Research local AI opportunities", List.of(), null, config(), "general_research"), request -> {
            requests.add(request);
            return switch (round.incrementAndGet()) {
                case 1 -> new AiProviderResponse("""
                        {"action":"plan","intent":"case_comparison","researchQuestions":["compare alpha"],
                         "toolRequests":[
                           {"requestId":"requestA","toolName":"search_cases","arguments":{"query":"alpha"},"dependsOn":[]},
                           {"requestId":"requestB","toolName":"search_cases","arguments":{"query":"beta"},"dependsOn":[]}
                         ],"comparisonDimensions":["businessModel"],
                         "outputSections":["directAnswer","comparison","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop");
                case 2 -> new AiProviderResponse("""
                        {"action":"continue","toolRequests":[
                          {"requestId":"comparison","toolName":"compare_cases","arguments":{"caseIds":[801,802],"dimensions":["businessModel"]},"dependsOn":["requestA"]}
                        ]}
                        """, 10, 5, 15, 10, "borrowed-ids", "stop");
                case 3 -> {
                    assertEquals(0, compareExecutions.get());
                    yield new AiProviderResponse("""
                            {"action":"continue","toolRequests":[
                              {"requestId":"comparison","toolName":"compare_cases","arguments":{"caseIds":[701,702],"dimensions":["businessModel"]},"dependsOn":["requestA"]}
                            ]}
                            """, 10, 5, 15, 10, "corrected-ids", "stop");
                }
                case 4 -> new AiProviderResponse("""
                        {"action":"final","answer":"The request A cases were compared.",
                         "citations":[{"sourceId":1701,"claim":"Case A1"},{"sourceId":1702,"claim":"Case A2"}],"confidence":0.8}
                        """, 10, 5, 15, 10, "final", "stop");
                default -> throw new AssertionError("unexpected model round");
            };
        }, progress -> { });

        assertEquals("completed", outcome.status());
        assertEquals(4, outcome.modelRounds());
        assertTrue(requests.get(2).messages().get(requests.get(2).messages().size() - 1).content()
                .contains("requestA"));
    }

    @Test
    void researchPlanUnknownFieldUsesASafeSpecificDiagnostic() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(),
                        mock(AiAgentToolCallMapper.class))
        );

        AgentOrchestratorException exception = assertThrows(
                AgentOrchestratorException.class,
                () -> orchestrator.execute(
                        input(config()),
                        request -> new AiProviderResponse(
                                "{\"action\":\"plan\",\"intent\":\"mixed_research\"," +
                                        "\"researchQuestions\":[\"湖北有哪些证据？\"]," +
                                        "\"toolRequests\":[],\"comparisonDimensions\":[]," +
                                        "\"outputSections\":[\"directAnswer\",\"citations\"]," +
                                        "\"unexpected\":true}",
                                10, 5, 15, 10, "plan-unknown", "stop"),
                        progress -> { }
                )
        );

        assertEquals("UNKNOWN_FIELDS", exception.getDiagnosticCode());
    }

    @Test
    void jsonPlanReceivesOnlyIndependentSearchSchemaWithoutDuplicatingTheToolCatalogInThePrompt() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentEvidenceToolMapper evidenceMapper = mock(AgentEvidenceToolMapper.class);
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(
                        new SearchCasesTool(evidenceMapper,
                                new com.opc.platform.ai.tool.AgentRegionResolver(mock(RegionMapper.class)),
                                objectMapper),
                        new SearchPoliciesTool(evidenceMapper, mock(RegionMapper.class),
                                new com.opc.platform.ai.tool.AgentRegionResolver(mock(RegionMapper.class)),
                                objectMapper),
                        new GetSourceTool(mock(SourceMapper.class), objectMapper),
                        new CompareCasesTool(evidenceMapper, objectMapper)
                ),
                objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(),
                mock(AiAgentToolCallMapper.class)
        );
        AtomicReference<com.opc.platform.ai.provider.AiProviderRequest> captured = new AtomicReference<>();

        new AgentOrchestrator(objectMapper, registry).execute(
                input(config()),
                request -> {
                    captured.set(request);
                    return new AiProviderResponse(
                            "{\"action\":\"evidence_insufficient\",\"answer\":\"No evidence.\"," +
                                    "\"citations\":[],\"confidence\":0.1}",
                            5, 3, 8, 4, "req-schema", "stop");
                },
                progress -> { }
        );

        String schema = captured.get().responseSchema();
        assertTrue(schema.contains("\"oneOf\""));
        assertTrue(schema.contains("\"search_cases\""));
        assertTrue(schema.contains("\"search_policies\""));
        assertTrue(schema.contains("\"scope\""));
        assertFalse(schema.contains("\"regionName\""));
        assertFalse(schema.contains("\"industryTagId\""));
        assertFalse(schema.contains("\"required\":[\"sourceId\"]"));
        assertFalse(schema.contains("\"const\":\"compare_cases\""));
        assertFalse(schema.contains("\"const\":\"get_source\""));
        assertTrue(schema.contains("\"additionalProperties\":false"));
        assertFalse(captured.get().systemPrompt().contains("Available tools and exact argument contracts"));
        assertTrue(captured.get().systemPrompt().contains("response schema"));
    }

    @Test
    void jsonPlanExecutesWhitelistedToolThenProducesCitedFinalAnswer() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            AiAgentToolCall call = invocation.getArgument(0);
            call.setId(71L);
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        AgentTool<SearchCasesArguments> fakeSearch = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "search"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                return new AgentToolResult(
                        objectMapper.valueToTree(java.util.Map.of("items", List.of(
                                java.util.Map.of("caseId", 11, "sourceId", 1, "title", "Case A")
                        ))),
                        1,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        Set.of(1L),
                        Set.of(11L)
                );
            }
        };
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(fakeSearch), objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), callMapper
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(objectMapper, registry);
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse(
                        "{\"action\":\"tool\",\"toolName\":\"search_cases\",\"arguments\":{\"regionId\":1,\"limit\":3}}",
                        10, 5, 15, 20, "req-1", "stop"),
                new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Case A is relevant.\"," +
                                "\"citations\":[{\"sourceId\":1,\"claim\":\"Case A supports the conclusion.\"}],\"confidence\":0.8}",
                        11, 4, 15, 18, "req-2", "stop")
        ));
        List<String> stages = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        91L, 42L, "{\"regionId\":1,\"industry\":\"AI\"}",
                        "Research local AI opportunities", List.of(),
                        new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan")
                ),
                request -> responses.removeFirst(),
                progress -> stages.add(progress.stage())
        );

        assertEquals("completed", outcome.status());
        assertEquals(2, outcome.modelRounds());
        assertEquals(1, outcome.toolCallCount());
        assertEquals(30, outcome.totalTokens());
        assertEquals(1L, outcome.citations().get(0).sourceId());
        assertTrue(stages.contains("tool_running"));
        assertTrue(stages.contains("synthesizing"));
    }

    @Test
    void researchV2ExecutesAClosedMultiToolPlanBeforeOneStructuredSynthesis() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        AtomicInteger toolAuditIds = new AtomicInteger();
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId((long) toolAuditIds.incrementAndGet());
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        AtomicReference<SearchCasesArguments> executedCaseArguments = new AtomicReference<>();
        AtomicReference<AgentToolContext> executedCaseContext = new AtomicReference<>();
        AgentTool<SearchCasesArguments> cases = fixtureTool(
                "search_cases", SearchCasesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"scope\":{\"type\":\"string\"}}}",
                objectMapper, 1L, 11L, executedCaseArguments, executedCaseContext
        );
        AgentTool<SearchPoliciesArguments> policies = fixtureTool(
                "search_policies", SearchPoliciesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"scope\":{\"type\":\"string\"}}}",
                objectMapper, 2L, null
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(cases, policies), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), callMapper)
        );
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("""
                        {"action":"plan","intent":"mixed_research",
                         "researchQuestions":["武汉有哪些可借鉴案例？","湖北有哪些适用政策？"],
                         "toolRequests":[
                           {"requestId":"cases","toolName":"search_cases","arguments":{"regionId":999,"industryTagId":99},"dependsOn":[]},
                           {"requestId":"policies","toolName":"search_policies","arguments":{"regionId":998},"dependsOn":[]}
                         ],
                         "comparisonDimensions":[],
                         "outputSections":["directAnswer","keyFindings","caseInsights","policyInsights","recommendations","risks","citations"]}
                        """, 12, 8, 20, 20, "plan-request", "stop"),
                new AiProviderResponse("""
                        {"action":"final","intent":"case_analysis",
                         "directAnswer":"湖北人工智能一人公司可以先验证本地服务需求，再申请通用创业支持。",
                         "keyFindings":[{"text":"武汉案例可能适合小预算验证。","evidenceType":"fact","sourceIds":[1]}],
                         "caseInsights":[{"text":"案例适合小预算先做服务验证。","evidenceType":"inference","sourceIds":[1]}],
                         "policyInsights":[{"text":"湖北存在通用创业支持政策。","evidenceType":"fact","sourceIds":[2]}],
                         "comparison":[],
                         "recommendations":[{"priority":"high","reason":"当前预算有限且处于验证阶段。","nextAction":"两周内访谈五位潜在客户。","sourceIds":[2]}],
                         "risks":["政策适用条件仍需逐条核验"],"assumptions":["团队以一人公司形式起步"],
                         "uncertainties":["客户付费意愿尚未验证"],"nextQuestions":["首批客户来自哪个细分行业？"],
                         "citations":[{"sourceId":1,"claim":"武汉案例支持小预算验证路径。"},{"sourceId":2,"claim":"湖北政策支持创业行动。"}],
                         "confidence":0.78,
                         "evidenceCoverage":{"status":"partial","caseCount":99,"policyCount":88,"sourceCount":77,"limitations":["模型声明仅供参考"]}}
                        """, 18, 12, 30, 30, "synthesis-request", "stop")
        ));
        AtomicInteger modelCalls = new AtomicInteger();
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        91L, 42L,
                        "{\"ventureType\":\"solo_company\",\"regionId\":2,\"industryTagId\":7,\"industry\":\"AI\",\"stage\":\"validation\",\"budgetRange\":\"under_100k\",\"goal\":\"validate demand\",\"resources\":\"prototype\"}",
                        "研究湖北人工智能一人公司的案例、政策和下一步行动", List.of(), config()
                ),
                request -> {
                    modelCalls.incrementAndGet();
                    requests.add(request);
                    return responses.removeFirst();
                },
                progress -> { }
        );

        assertEquals("completed", outcome.status());
        assertEquals(2, outcome.modelRounds());
        assertEquals(2, outcome.toolCallCount());
        assertEquals(2, modelCalls.get());
        assertEquals("selected", executedCaseArguments.get().getScope());
        assertEquals(2L, executedCaseContext.get().primaryRegionId());
        assertEquals(7L, executedCaseContext.get().primaryIndustryTagId());
        assertEquals(2, outcome.citations().size());
        assertEquals("mixed_research", outcome.structuredResult().path("intent").asText());
        assertEquals("fact", outcome.structuredResult().path("keyFindings").get(0)
                .path("evidenceType").asText());
        assertEquals(1, outcome.structuredResult().path("keyFindings").get(0)
                .path("sourceIds").size());
        assertEquals(1, outcome.structuredResult().path("caseInsights").get(0)
                .path("sourceIds").size());
        assertEquals(1, outcome.structuredResult().path("recommendations").get(0)
                .path("sourceIds").size());
        assertEquals(2, outcome.structuredResult().path("citations").size());
        assertEquals("sufficient", outcome.structuredResult().path("evidenceCoverage").path("status").asText());
        assertEquals(1, outcome.structuredResult().path("evidenceCoverage").path("caseCount").asInt());
        assertEquals(1, outcome.structuredResult().path("evidenceCoverage").path("policyCount").asInt());
        assertEquals(2, outcome.structuredResult().path("evidenceCoverage").path("sourceCount").asInt());
        assertEquals("EVIDENCE_COVERAGE_MISMATCH", outcome.structuredResult()
                .path("evidenceCoverage").path("diagnosticCode").asText());
        assertTrue(outcome.structuredResult().path("citations").get(0).path("sourceId").isIntegralNumber());
        assertTrue(outcome.answer().contains("高优先级"));
        assertEquals("agent-research-v2", requests.get(0).promptVersion());
        var planningSchema = objectMapper.readTree(requests.get(0).responseSchema());
        assertEquals(1, planningSchema.path("oneOf").size());
        assertEquals("plan", planningSchema.path("oneOf").get(0)
                .path("properties").path("action").path("const").asText());
        assertEquals(3200, requests.get(0).maxOutputTokens());
        assertFalse(requests.get(0).messages().get(0).content().contains("Available tools"));
        assertTrue(requests.get(0).responseSchema().contains("search_cases"));
        var synthesisSchema = objectMapper.readTree(requests.get(1).responseSchema());
        assertEquals(2, synthesisSchema.path("oneOf").size());
        assertEquals("final", synthesisSchema.path("oneOf").get(0)
                .path("properties").path("action").path("const").asText());
        assertEquals(1, synthesisSchema.path("oneOf").get(0)
                .path("properties").path("citations").path("minItems").asInt());
        assertEquals("evidence_insufficient", synthesisSchema.path("oneOf").get(1)
                .path("properties").path("action").path("const").asText());
        assertEquals(0, synthesisSchema.path("oneOf").get(1)
                .path("properties").path("citations").path("maxItems").asInt());
        assertEquals(3200, requests.get(1).maxOutputTokens());
        assertTrue(!requests.get(1).messages().get(0).content().contains("Available tools"));
        assertTrue(requests.get(1).messages().get(0).content().contains("Keep the result compact"));
        String synthesisEvidence = requests.get(1).messages().get(requests.get(1).messages().size() - 1).content();
        assertTrue(synthesisEvidence.contains("\"sourceId\":1"));
        assertTrue(synthesisEvidence.contains("\"caseId\""));
        assertTrue(synthesisEvidence.contains("\"policyId\""));
    }

    @Test
    void sourceContractConflictGetsOneBoundedSynthesisRecovery() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId(72L);
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        AgentTool<SearchCasesArguments> cases = fixtureTool(
                "search_cases", SearchCasesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"regionId\"],\"properties\":{\"regionId\":{\"type\":\"integer\"}}}",
                objectMapper, 1L, 11L
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(cases), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), callMapper)
        );
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis",
                         "researchQuestions":["one","two","three","four"],
                         "toolRequests":[],"comparisonDimensions":[],
                         "outputSections":["directAnswer","citations"]}
                        """, 8, 4, 12, 15, "invalid-plan", "stop"),
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis","researchQuestions":["local cases"],
                         "toolRequests":[{"requestId":"cases","toolName":"search_cases","arguments":{"regionId":2},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","caseInsights","recommendations","citations"]}
                        """, 10, 5, 15, 20, "plan", "stop"),
                new AiProviderResponse("""
                        {"action":"final","intent":"case_analysis","directAnswer":"Evidence is available.",
                          "keyFindings":[],"caseInsights":[],"policyInsights":[],"comparison":[],"recommendations":[],
                          "risks":[],"assumptions":[],"uncertainties":[],"nextQuestions":[],
                          "citations":[{"sourceId":999,"claim":"The verified case is relevant."}],"confidence":0.4,
                          "evidenceCoverage":{"status":"partial","caseCount":1,"policyCount":0,"sourceCount":1,"limitations":[]}}
                        """, 20, 8, 28, 30, "invalid-final", "stop"),
                new AiProviderResponse("""
                        {"action":"final","intent":"case_analysis","directAnswer":"Use the verified local case as a bounded reference.",
                         "keyFindings":[],"caseInsights":[{"text":"The local case is relevant.","evidenceType":"fact","sourceIds":[1]}],
                         "policyInsights":[],"comparison":[],
                         "recommendations":[{"priority":"high","reason":"It matches the selected region.","nextAction":"Validate one customer segment.","sourceIds":[1]}],
                         "risks":[],"assumptions":[],"uncertainties":[],"nextQuestions":[],
                         "citations":[{"sourceId":1,"claim":"The verified case supports the recommendation."}],"confidence":0.7,
                         "evidenceCoverage":{"status":"partial","caseCount":1,"policyCount":0,"sourceCount":1,"limitations":[]}}
                        """, 20, 10, 30, 30, "repaired-final", "stop")
        ));
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        91L, 42L, "{\"regionId\":2,\"industry\":\"AI\"}",
                        "Research local AI opportunities", List.of(), config()
                ),
                request -> {
                    requests.add(request);
                    return responses.removeFirst();
                },
                progress -> { }
        );

        assertEquals("completed", outcome.status());
        assertEquals(4, outcome.modelRounds());
        assertEquals(4, requests.size());
        assertTrue(requests.get(1).messages().stream().anyMatch(message ->
                message.content() != null && message.content().contains("INVALID_RESEARCH_QUESTIONS")));
        String repairInstruction = requests.get(3).messages()
                .get(requests.get(3).messages().size() - 1).content();
        assertTrue(repairInstruction.contains("authorized sourceId values are [1]"));
        assertFalse(requests.get(3).messages().stream()
                .anyMatch(message -> message.content() != null && message.content().contains("invalid-final")));
    }

    @Test
    void twoDistinctSourceContractFailuresGetTwoBoundedRecoveries() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId(73L);
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        AgentTool<SearchCasesArguments> cases = fixtureTool(
                "search_cases", SearchCasesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}",
                objectMapper, 1L, 11L
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(cases), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), callMapper)
        );
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis","researchQuestions":["local case"],
                         "toolRequests":[{"requestId":"cases","toolName":"search_cases","arguments":{},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","keyFindings","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop"),
                new AiProviderResponse("""
                        {"action":"final","intent":"case_analysis","directAnswer":"Use the verified case.",
                         "keyFindings":[{"text":"The case is locally relevant.","evidenceType":"fact","sourceIds":[]}],
                         "caseInsights":[],"policyInsights":[],"comparison":[],"recommendations":[],
                         "risks":[],"assumptions":[],"uncertainties":[],"nextQuestions":[],
                         "citations":[{"sourceId":1,"claim":"Verified case"}],"confidence":0.7,
                         "evidenceCoverage":{"status":"partial","caseCount":1,"policyCount":0,"sourceCount":1,"limitations":[]}}
                        """, 10, 5, 15, 10, "uncited-fact", "stop"),
                new AiProviderResponse("""
                        {"action":"final","intent":"case_analysis","directAnswer":"Use the verified case.",
                         "keyFindings":[{"text":"The case is locally relevant.","evidenceType":"fact","sourceIds":[1]}],
                         "caseInsights":[],"policyInsights":[],"comparison":[],
                         "recommendations":[{"priority":"high","reason":"Use the case","nextAction":"Validate","sourceIds":[]}],
                         "risks":[],"assumptions":[],"uncertainties":[],"nextQuestions":[],
                         "citations":[{"sourceId":1,"claim":"Verified case"}],"confidence":0.7,
                         "evidenceCoverage":{"status":"partial","caseCount":1,"policyCount":0,"sourceCount":1,"limitations":[]}}
                        """, 10, 5, 15, 10, "uncited-recommendation", "stop"),
                new AiProviderResponse("""
                        {"action":"final","intent":"case_analysis","directAnswer":"Use the verified case.",
                         "keyFindings":[{"text":"The case is locally relevant.","evidenceType":"fact","sourceIds":[1]}],
                         "caseInsights":[],"policyInsights":[],"comparison":[],"recommendations":[],
                         "risks":[],"assumptions":[],"uncertainties":[],"nextQuestions":[],
                         "citations":[{"sourceId":1,"claim":"Verified case"}],"confidence":0.7,
                         "evidenceCoverage":{"status":"partial","caseCount":1,"policyCount":0,"sourceCount":1,"limitations":[]}}
                        """, 10, 5, 15, 10, "recovered-final", "stop")
        ));
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(input(config()), request -> {
            requests.add(request);
            return responses.removeFirst();
        }, progress -> { });

        assertEquals("completed", outcome.status());
        assertEquals(4, outcome.modelRounds());
        assertEquals(1, outcome.toolCallCount());
        String recovery = requests.get(2).messages().get(requests.get(2).messages().size() - 1).content();
        assertTrue(recovery.contains("UNCITED_FACT"));
        assertTrue(recovery.contains("[1]"));
        assertFalse(requests.get(2).messages().stream().anyMatch(message ->
                message.content() != null && message.content().contains("uncited-fact")));
        String secondRecovery = requests.get(3).messages()
                .get(requests.get(3).messages().size() - 1).content();
        assertTrue(secondRecovery.contains("UNCITED_RECOMMENDATION"));
        assertFalse(requests.get(3).messages().stream().anyMatch(message ->
                message.content() != null && message.content().contains("uncited-recommendation")));
    }

    @Test
    void unknownCitationKeepsInvalidSourceDiagnosticAndGetsOneRepair() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool<SearchCasesArguments> cases = fixtureTool(
                "search_cases", SearchCasesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}",
                objectMapper, 1L, 11L
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(cases), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), guardedCallMapper())
        );
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis","researchQuestions":["local case"],
                         "toolRequests":[{"requestId":"cases","toolName":"search_cases","arguments":{},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop"),
                new AiProviderResponse("""
                        {"action":"final","answer":"Unsupported citation was supplied.",
                         "citations":[{"sourceId":999,"claim":"Unknown"}],"confidence":0.6}
                        """, 10, 5, 15, 10, "unknown-citation", "stop"),
                new AiProviderResponse("""
                        {"action":"final","answer":"The authorized case supports the bounded answer.",
                         "citations":[{"sourceId":1,"claim":"Authorized case"}],"confidence":0.7}
                        """, 10, 5, 15, 10, "repaired-citation", "stop")
        ));
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(input(config()), request -> {
            requests.add(request);
            return responses.removeFirst();
        }, progress -> { });

        assertEquals("completed", outcome.status());
        assertEquals(1L, outcome.citations().get(0).sourceId());
        String recovery = requests.get(2).messages().get(requests.get(2).messages().size() - 1).content();
        assertTrue(recovery.contains("INVALID_SOURCE_ID"));
        assertTrue(recovery.contains("[1]"));
        assertFalse(recovery.contains("999"));
    }

    @Test
    void continuationSchemaUsesRemainingToolBudgetAndOversizedRequestGetsOneRepair() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool<SearchCasesArguments> cases = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "search"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                var output = objectMapper.createObjectNode();
                output.putArray("items")
                        .addObject().put("caseId", 701L).put("sourceId", 1L);
                output.withArray("items")
                        .addObject().put("caseId", 702L).put("sourceId", 2L);
                return new AgentToolResult(
                        output, 2, "s".repeat(64), Set.of(1L, 2L), Set.of(701L, 702L));
            }
        };
        AgentTool<CompareCasesArguments> compare = new AgentTool<>() {
            public String name() { return "compare_cases"; }
            public String description() { return "compare"; }
            public Class<CompareCasesArguments> argumentType() { return CompareCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"caseIds\"],"
                        + "\"properties\":{\"caseIds\":{\"type\":\"array\",\"minItems\":2,\"items\":{\"type\":\"integer\"}}}}";
            }
            public AgentToolResult execute(AgentToolContext context, CompareCasesArguments arguments) {
                var output = objectMapper.createObjectNode();
                output.putArray("cases").addObject().put("caseId", 701L).put("sourceId", 1L);
                return new AgentToolResult(output, 1, "c".repeat(64), Set.of(1L), Set.of(701L));
            }
        };
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(cases, compare), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), guardedCallMapper())
        );
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_comparison","researchQuestions":["case"],
                         "toolRequests":[{"requestId":"cases","toolName":"search_cases","arguments":{},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop"),
                new AiProviderResponse("""
                        {"action":"continue","toolRequests":[
                          {"requestId":"c1","toolName":"compare_cases","arguments":{"caseIds":[701,702]},"dependsOn":["cases"]},
                          {"requestId":"c2","toolName":"compare_cases","arguments":{"caseIds":[701,702]},"dependsOn":["cases"]}
                        ]}
                        """, 10, 5, 15, 10, "oversized", "stop"),
                new AiProviderResponse("""
                        {"action":"continue","toolRequests":[
                          {"requestId":"comparison","toolName":"compare_cases","arguments":{"caseIds":[701,702]},"dependsOn":["cases"]}
                        ]}
                        """, 10, 5, 15, 10, "repaired", "stop"),
                new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Compared.\",\"citations\":[{\"sourceId\":1,\"claim\":\"Case\"}],\"confidence\":0.7}",
                        10, 5, 15, 10, "final", "stop")
        ));
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        91L, 42L, "{\"regionId\":1,\"industry\":\"AI\"}",
                        "Research local AI opportunities", List.of(), null,
                        new AgentRuntimeConfig(true, 4, 2, 8000, 12,
                                Duration.ofSeconds(120), "json_plan"),
                        "general_research"),
                request -> {
                    requests.add(request);
                    return responses.removeFirst();
                }, progress -> { }
        );

        assertEquals("completed", outcome.status());
        assertEquals(2, outcome.toolCallCount());
        JsonNode continuation = objectMapper.readTree(requests.get(1).responseSchema()).path("oneOf").get(0);
        assertEquals(1, continuation.path("properties").path("toolRequests").path("maxItems").asInt());
        assertTrue(requests.get(2).messages().get(requests.get(2).messages().size() - 1).content()
                .contains("INVALID_TOOL_REQUESTS"));
    }

    @Test
    void multiroundRequestsCarryOnlyOneCurrentEvidenceBundle() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool<SearchCasesArguments> cases = fixtureTool(
                "search_cases", SearchCasesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}",
                objectMapper, 1L, 701L
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(cases), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), guardedCallMapper())
        );
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis","researchQuestions":["case"],
                         "toolRequests":[{"requestId":"casesA","toolName":"search_cases","arguments":{},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop"),
                new AiProviderResponse("""
                        {"action":"continue","toolRequests":[
                          {"requestId":"casesB","toolName":"search_cases","arguments":{},"dependsOn":[]}
                        ]}
                        """, 10, 5, 15, 10, "continue", "stop"),
                new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Bounded.\",\"citations\":[{\"sourceId\":1,\"claim\":\"Case\"}],\"confidence\":0.7}",
                        10, 5, 15, 10, "final", "stop")
        ));
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                input(new AgentRuntimeConfig(true, 4, 4, 8000, 12, Duration.ofSeconds(120), "json_plan")),
                request -> {
                    requests.add(request);
                    return responses.removeFirst();
                }, progress -> { }
        );

        assertEquals("completed", outcome.status());
        long evidenceMessages = requests.get(2).messages().stream()
                .filter(message -> message.content() != null
                        && message.content().contains("Verified evidence bundle:"))
                .count();
        assertEquals(1, evidenceMessages);
    }

    @Test
    void invalidSearchArgumentsAndContinuationUnknownFieldsGetIndependentBoundedCorrections() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool<SearchPoliciesArguments> policies = fixtureTool(
                "search_policies", SearchPoliciesArguments.class,
                """
                {"type":"object","additionalProperties":false,"properties":{
                  "scope":{"type":"string","enum":["selected","parent","national"]},
                  "query":{"type":"string","maxLength":120},
                  "limit":{"type":"integer","minimum":1,"maximum":10}
                }}
                """,
                objectMapper, 1L, null
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(policies), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), guardedCallMapper())
        );
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("""
                        {"action":"plan","intent":"general_research","researchQuestions":["policy"],
                         "toolRequests":[{"requestId":"badPolicies","toolName":"search_policies","arguments":{"scope":"province"},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop"),
                new AiProviderResponse("""
                        {"action":"continue","toolRequests":[
                          {"requestId":"policies","toolName":"search_policies","arguments":{"scope":"selected","limit":5},"dependsOn":[]}
                        ],"unexpected":true}
                        """, 10, 5, 15, 10, "unknown-fields", "stop"),
                new AiProviderResponse("""
                        {"action":"continue","toolRequests":[
                          {"requestId":"policies","toolName":"search_policies","arguments":{"scope":"selected","limit":5},"dependsOn":[]}
                        ]}
                        """, 10, 5, 15, 10, "corrected", "stop"),
                new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Policy found.\",\"citations\":[{\"sourceId\":1,\"claim\":\"Policy\"}],\"confidence\":0.7}",
                        10, 5, 15, 10, "final", "stop")
        ));
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                input(new AgentRuntimeConfig(true, 5, 4, 8000, 12, Duration.ofSeconds(120), "json_plan")),
                request -> {
                    requests.add(request);
                    return responses.removeFirst();
                }, progress -> { }
        );

        assertEquals("completed", outcome.status());
        assertEquals(2, outcome.toolCallCount());
        assertTrue(requests.get(1).responseSchema().contains("\"const\":\"continue\""));
        assertFalse(requests.get(1).responseSchema().contains("\"const\":\"plan\""));
        String recovery = requests.get(1).messages().get(requests.get(1).messages().size() - 1).content();
        assertTrue(recovery.contains("INVALID_TOOL_ARGUMENTS"));
        assertFalse(recovery.contains("province"));
        String continuationRecovery = requests.get(2).messages()
                .get(requests.get(2).messages().size() - 1).content();
        assertTrue(continuationRecovery.contains("UNKNOWN_FIELDS"));
    }

    @Test
    void explicitPolicyRequestCompletesWhenModelMisclassifiesItAsCaseAnalysis() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool<SearchPoliciesArguments> policies = fixtureTool(
                "search_policies", SearchPoliciesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}",
                objectMapper, 1L, null
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(policies), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), guardedCallMapper())
        );
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis","researchQuestions":["policy"],
                         "toolRequests":[{"requestId":"policies","toolName":"search_policies","arguments":{},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop"),
                new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Policy found.\",\"citations\":[{\"sourceId\":1,\"claim\":\"Policy\"}],\"confidence\":0.7}",
                        10, 5, 15, 10, "final", "stop")
        ));
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        91L, 42L, "{\"regionId\":2,\"industry\":\"AI\"}",
                        "请查找适用的扶持政策和补贴条件", List.of(),
                        null,
                        new AgentRuntimeConfig(true, 5, 4, 8000, 12, Duration.ofSeconds(120), "json_plan"),
                        "policy_lookup"),
                request -> {
                    requests.add(request);
                    return responses.removeFirst();
                }, progress -> { }
        );

        assertEquals("completed", outcome.status());
        assertEquals(2, outcome.modelRounds());
        assertEquals(1, outcome.toolCallCount());
        assertEquals(2, requests.size());
        assertFalse(requests.get(1).responseSchema().contains("\"const\":\"continue\""));
    }

    @Test
    void invalidInitialActionGetsOneFreshPlanRecoveryWithoutPersistingRawOutput() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool<SearchCasesArguments> cases = fixtureTool(
                "search_cases", SearchCasesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}",
                objectMapper, 1L, 701L
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(cases), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), guardedCallMapper())
        );
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("{\"action\":\"continue\",\"toolRequests\":[]}",
                        10, 5, 15, 10, "invalid-action", "stop"),
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis","researchQuestions":["case"],
                         "toolRequests":[{"requestId":"cases","toolName":"search_cases","arguments":{},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","citations"]}
                        """, 10, 5, 15, 10, "plan", "stop"),
                new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Case found.\",\"citations\":[{\"sourceId\":1,\"claim\":\"Case\"}],\"confidence\":0.7}",
                        10, 5, 15, 10, "final", "stop")
        ));
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                input(new AgentRuntimeConfig(true, 5, 4, 24000, 12, Duration.ofSeconds(120), "json_plan")),
                request -> {
                    requests.add(request);
                    return responses.removeFirst();
                }, progress -> { }
        );

        assertEquals("completed", outcome.status());
        assertEquals(3, outcome.modelRounds());
        assertTrue(requests.get(1).messages().get(requests.get(1).messages().size() - 1).content()
                .contains("INVALID_AGENT_ACTION"));
        assertFalse(requests.get(1).messages().stream().anyMatch(message ->
                message.content() != null && message.content().contains("invalid-action")));
    }

    @Test
    void twoDistinctPlanningFailuresGetTwoBoundedRecoveriesBeforeToolExecution() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId(81L);
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        AgentTool<SearchCasesArguments> cases = fixtureTool(
                "search_cases", SearchCasesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"regionId\"],\"properties\":{\"regionId\":{\"type\":\"integer\"}}}",
                objectMapper, 1L, 11L
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(cases), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), callMapper)
        );
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                new AiProviderResponse("TRUNCATED_PRIVATE_CONTENT", 1400, 1600, 3000, 20,
                        "truncated-plan", "length"),
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis",
                         "researchQuestions":["one","two","three","four"],
                         "toolRequests":[{"requestId":"cases","toolName":"search_cases","arguments":{"regionId":2},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","caseInsights","citations"]}
                        """, 500, 300, 800, 20, "invalid-questions", "stop"),
                new AiProviderResponse("""
                        {"action":"plan","intent":"case_analysis",
                         "researchQuestions":["武汉有哪些可借鉴案例？"],
                         "toolRequests":[{"requestId":"cases","toolName":"search_cases","arguments":{"regionId":2},"dependsOn":[]}],
                         "comparisonDimensions":[],"outputSections":["directAnswer","caseInsights","citations"]}
                        """, 500, 300, 800, 20, "recovered-plan", "stop"),
                new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Verified case evidence.\"," +
                                "\"citations\":[{\"sourceId\":1,\"claim\":\"Case evidence was used.\"}],\"confidence\":0.7}",
                        1000, 500, 1500, 20, "recovered-final", "stop")
        ));
        List<com.opc.platform.ai.provider.AiProviderRequest> requests = new ArrayList<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                input(config()),
                request -> {
                    requests.add(request);
                    return responses.removeFirst();
                },
                progress -> { }
        );

        assertEquals("completed", outcome.status());
        assertEquals(4, outcome.modelRounds());
        assertEquals(1, outcome.toolCallCount());
        assertEquals(4, requests.size());
        assertTrue(requests.get(0).responseSchema() != null);
        assertTrue(requests.get(1).responseSchema() != null);
        assertTrue(requests.get(1).responseSchema().contains("\"const\":\"plan\""));
        assertEquals(requests.get(0).responseSchema(), requests.get(1).responseSchema());
        assertTrue(requests.get(1).systemPrompt().contains("compact planning recovery"));
        assertEquals(3200, requests.get(1).maxOutputTokens());
        assertTrue(requests.get(2).messages().get(requests.get(2).messages().size() - 1).content()
                .contains("INVALID_RESEARCH_QUESTIONS"));
        assertTrue(requests.get(1).messages().stream()
                .noneMatch(message -> message.content() != null && message.content().contains("TRUNCATED_PRIVATE_CONTENT")));
    }

    @Test
    void nativeModeKeepsToolsAndRequiresStructuredFinalPayload() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(), objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), mock(AiAgentToolCallMapper.class)
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(objectMapper, registry);
        AtomicReference<com.opc.platform.ai.provider.AiProviderRequest> captured = new AtomicReference<>();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        91L, 42L, "{\"regionId\":1,\"industry\":\"AI\"}",
                        "Research local AI opportunities", List.of(),
                        new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "native")
                ),
                request -> {
                    captured.set(request);
                    return new AiProviderResponse(
                            "{\"action\":\"evidence_insufficient\",\"answer\":\"No verified evidence.\"," +
                                    "\"citations\":[],\"confidence\":0.2}",
                            8, 4, 12, 10, "req-native", "stop");
                },
                stages -> { }
        );

        assertEquals("evidence_insufficient", outcome.status());
        assertTrue(captured.get().jsonResponse());
        assertTrue(captured.get().responseSchema().contains("evidence_insufficient"));
    }

    @Test
    void contentFilterFinishReasonHasStableDiagnostic() {
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                new ObjectMapper(),
                new AgentToolRegistry(List.of(), new ObjectMapper(),
                        Validation.buildDefaultValidatorFactory().getValidator(), mock(AiAgentToolCallMapper.class))
        );

        AgentOrchestratorException exception = org.junit.jupiter.api.Assertions.assertThrows(
                AgentOrchestratorException.class,
                () -> orchestrator.execute(
                        new AgentOrchestratorInput(
                                91L, 42L, "{\"regionId\":1,\"industry\":\"AI\"}", "Research",
                                List.of(), new AgentRuntimeConfig(
                                true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan")
                        ),
                        request -> new AiProviderResponse("{}", 3, 0, 3, 5, "req-filter", "content_filter"),
                        stage -> { }
                )
        );

        assertEquals("CONTENT_FILTERED", exception.getDiagnosticCode());
    }

    @Test
    void modelRoundAndToolCallLimitsStopTheLoopWithStableDiagnostics() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentOrchestrator orchestrator = new AgentOrchestrator(objectMapper, registryWithSearch(objectMapper));
        AiProviderResponse tool = new AiProviderResponse(
                "{\"action\":\"tool\",\"toolName\":\"search_cases\",\"arguments\":{\"regionId\":1}}",
                3, 2, 5, 5, "req-tool", "stop");

        AgentOrchestratorException roundLimit = assertThrows(AgentOrchestratorException.class, () ->
                orchestrator.execute(
                        input(new AgentRuntimeConfig(true, 1, 6, 8000, 12, Duration.ofSeconds(120), "json_plan")),
                        request -> tool, progress -> { }
                ));
        assertEquals("AGENT_ROUND_LIMIT", roundLimit.getDiagnosticCode());

        ArrayDeque<AiProviderResponse> twoTools = new ArrayDeque<>(List.of(tool, tool));
        AgentOrchestratorException toolLimit = assertThrows(AgentOrchestratorException.class, () ->
                orchestrator.execute(
                        input(new AgentRuntimeConfig(true, 4, 1, 8000, 12, Duration.ofSeconds(120), "json_plan")),
                        request -> twoTools.removeFirst(), progress -> { }
                ));
        assertEquals("AGENT_TOOL_LIMIT", toolLimit.getDiagnosticCode());
    }

    @Test
    void tokenCitationAndUnknownFinishFailuresUseControlledDiagnostics() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentOrchestrator orchestrator = new AgentOrchestrator(objectMapper, registryWithSearch(objectMapper));

        AgentOrchestratorException tokenLimit = assertThrows(AgentOrchestratorException.class, () ->
                orchestrator.execute(input(config()), request -> new AiProviderResponse(
                        "{}", 5000, 4000, 9000, 5, "req-token", "stop"), progress -> { }));
        assertEquals("AGENT_TOKEN_LIMIT", tokenLimit.getDiagnosticCode());

        AgentOrchestratorException missingCitations = assertThrows(AgentOrchestratorException.class, () ->
                orchestrator.execute(input(config()), request -> new AiProviderResponse(
                        "{\"action\":\"final\",\"answer\":\"Unsupported answer.\",\"citations\":[],\"confidence\":0.5}",
                        3, 2, 5, 5, "req-citations", "stop"), progress -> { }));
        assertEquals("MISSING_CITATIONS", missingCitations.getDiagnosticCode());

        AgentOrchestratorException abnormal = assertThrows(AgentOrchestratorException.class, () ->
                orchestrator.execute(input(config()), request -> new AiProviderResponse(
                        "{}", 3, 0, 3, 5, "req-abnormal", "safety"), progress -> { }));
        assertEquals("ABNORMAL_FINISH_REASON", abnormal.getDiagnosticCode());
    }

    private AgentOrchestratorInput input(AgentRuntimeConfig config) {
        return new AgentOrchestratorInput(
                91L, 42L, "{\"regionId\":1,\"industry\":\"AI\"}",
                "Research local AI opportunities", List.of(), config
        );
    }

    private ComparisonScenario runServerOwnedComparisonScenario(
            List<Long> selectedCaseIds,
            List<Long> crossRegionCaseIds,
            boolean expectCompleted
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool<SearchCasesArguments> search = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "search cases by controlled scope"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{"
                        + "\"scope\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                List<Long> caseIds = "cross_region_reference".equals(arguments.getScope())
                        ? crossRegionCaseIds : selectedCaseIds;
                var output = objectMapper.createObjectNode();
                var items = output.putArray("items");
                java.util.LinkedHashSet<Long> sources = new java.util.LinkedHashSet<>();
                java.util.LinkedHashSet<Long> cases = new java.util.LinkedHashSet<>();
                for (Long caseId : caseIds) {
                    long sourceId = caseId + 100;
                    items.addObject().put("caseId", caseId).put("sourceId", sourceId)
                            .put("title", "Case " + caseId);
                    cases.add(caseId);
                    sources.add(sourceId);
                }
                String hash = "cross_region_reference".equals(arguments.getScope())
                        ? "b".repeat(64) : "a".repeat(64);
                return new AgentToolResult(output, cases.size(), hash, sources, cases);
            }
        };
        AtomicReference<List<Long>> compared = new AtomicReference<>(List.of());
        AgentTool<CompareCasesArguments> compare = new AgentTool<>() {
            public String name() { return "compare_cases"; }
            public String description() { return "compare authorized cases"; }
            public Class<CompareCasesArguments> argumentType() { return CompareCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"caseIds\"],"
                        + "\"properties\":{\"caseIds\":{\"type\":\"array\",\"minItems\":2,\"maxItems\":3,"
                        + "\"items\":{\"type\":\"integer\"}},\"dimensions\":{\"type\":\"array\"}}}";
            }
            public AgentToolResult execute(AgentToolContext context, CompareCasesArguments arguments) {
                compared.set(List.copyOf(arguments.getCaseIds()));
                var output = objectMapper.createObjectNode();
                var cases = output.putArray("cases");
                java.util.LinkedHashSet<Long> sources = new java.util.LinkedHashSet<>();
                for (Long caseId : arguments.getCaseIds()) {
                    long sourceId = caseId + 100;
                    cases.addObject().put("caseId", caseId).put("sourceId", sourceId);
                    sources.add(sourceId);
                }
                return new AgentToolResult(
                        output, arguments.getCaseIds().size(), "c".repeat(64), sources,
                        Set.copyOf(arguments.getCaseIds()));
            }
        };
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                objectMapper,
                new AgentToolRegistry(List.of(search, compare), objectMapper,
                        Validation.buildDefaultValidatorFactory().getValidator(), guardedCallMapper())
        );
        AtomicInteger round = new AtomicInteger();
        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        91L, 42L, "{\"regionId\":2,\"industry\":\"AI\"}",
                        "Compare two verified cases", List.of(), null,
                        new AgentRuntimeConfig(true, 5, 6, 28000, 12,
                                Duration.ofSeconds(120), "json_plan"),
                        "case_comparison"),
                request -> switch (round.incrementAndGet()) {
                    case 1 -> new AiProviderResponse("""
                            {"action":"plan","intent":"case_analysis","researchQuestions":["compare cases"],
                             "toolRequests":[{"requestId":"casesSelected","toolName":"search_cases","arguments":{},"dependsOn":[]}],
                             "comparisonDimensions":["businessModel"],
                             "outputSections":["directAnswer","comparison","citations"]}
                            """, 10, 5, 15, 10, "plan", "stop");
                    case 2 -> expectCompleted
                            ? new AiProviderResponse(
                            "{\"action\":\"final\",\"answer\":\"Compared.\",\"citations\":["
                                    + "{\"sourceId\":801,\"claim\":\"Case 701\"},"
                                    + "{\"sourceId\":802,\"claim\":\"Case 702\"}],\"confidence\":0.8}",
                            10, 5, 15, 10, "final", "stop")
                            : new AiProviderResponse(
                            "{\"action\":\"evidence_insufficient\",\"answer\":\"Only one distinct case.\","
                                    + "\"citations\":[],\"confidence\":0.2}",
                            10, 5, 15, 10, "insufficient", "stop");
                    default -> throw new AssertionError("unexpected model round");
                }, progress -> { }
        );
        return new ComparisonScenario(outcome, compared.get());
    }

    private AgentRuntimeConfig config() {
        return new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan");
    }

    private record ComparisonScenario(
            AgentOrchestratorOutcome outcome,
            List<Long> comparedCaseIds
    ) { }

    private AiAgentToolCallMapper guardedCallMapper() {
        AiAgentToolCallMapper mapper = mock(AiAgentToolCallMapper.class);
        AtomicInteger auditId = new AtomicInteger();
        when(mapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId((long) auditId.incrementAndGet());
            return 1;
        });
        when(mapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        return mapper;
    }

    private AgentToolRegistry registryWithSearch(ObjectMapper objectMapper) {
        AgentTool<SearchCasesArguments> search = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "search"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                return new AgentToolResult(
                        objectMapper.valueToTree(java.util.Map.of("items", List.of())), 0,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        Set.of(), Set.of()
                );
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

    private <T> AgentTool<T> fixtureTool(
            String name,
            Class<T> type,
            String schema,
            ObjectMapper objectMapper,
            Long sourceId,
            Long caseId
    ) {
        return fixtureTool(name, type, schema, objectMapper, sourceId, caseId, null, null);
    }

    private <T> AgentTool<T> fixtureTool(
            String name,
            Class<T> type,
            String schema,
            ObjectMapper objectMapper,
            Long sourceId,
            Long caseId,
            AtomicReference<T> capturedArguments
    ) {
        return fixtureTool(name, type, schema, objectMapper, sourceId, caseId, capturedArguments, null);
    }

    private <T> AgentTool<T> fixtureTool(
            String name,
            Class<T> type,
            String schema,
            ObjectMapper objectMapper,
            Long sourceId,
            Long caseId,
            AtomicReference<T> capturedArguments,
            AtomicReference<AgentToolContext> capturedContext
    ) {
        return new AgentTool<>() {
            public String name() { return name; }
            public String description() { return "fixture " + name; }
            public Class<T> argumentType() { return type; }
            public String argumentSchema() { return schema; }
            public AgentToolResult execute(AgentToolContext context, T arguments) {
                if (capturedArguments != null) capturedArguments.set(arguments);
                if (capturedContext != null && context.primaryRegionId() != null) capturedContext.set(context);
                var item = objectMapper.createObjectNode();
                item.put(name.equals("search_cases") ? "caseId" : "policyId", caseId == null ? 21L : caseId);
                item.put("sourceId", sourceId);
                item.put("title", name);
                var output = objectMapper.createObjectNode();
                output.putArray("items").add(item);
                return new AgentToolResult(
                        output, 1,
                        String.valueOf(name.hashCode()).repeat(64).substring(0, 64),
                        Set.of(sourceId), caseId == null ? Set.of() : Set.of(caseId)
                );
            }
        };
    }
}
