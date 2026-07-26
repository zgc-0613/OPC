package com.opc.platform.ai.service;

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
import com.opc.platform.ai.tool.CompareCasesTool;
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
    void jsonPlanReceivesCompleteSchemaWithoutDuplicatingTheToolCatalogInThePrompt() {
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
        assertTrue(schema.contains("\"required\":[\"regionId\"]"));
        assertTrue(schema.contains("\"required\":[\"sourceId\"]"));
        assertTrue(schema.contains("\"minItems\":2"));
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
                        "Compare local AI opportunities", List.of(),
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
        AgentTool<SearchCasesArguments> cases = fixtureTool(
                "search_cases", SearchCasesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"regionId\"],\"properties\":{\"regionId\":{\"type\":\"integer\"},\"industryTagId\":{\"type\":\"integer\"}}}",
                objectMapper, 1L, 11L, executedCaseArguments
        );
        AgentTool<SearchPoliciesArguments> policies = fixtureTool(
                "search_policies", SearchPoliciesArguments.class,
                "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"regionId\"],\"properties\":{\"regionId\":{\"type\":\"integer\"},\"industryTagId\":{\"type\":\"integer\"}}}",
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
                           {"requestId":"cases","toolName":"search_cases","arguments":{"regionId":999},"dependsOn":[]},
                           {"requestId":"policies","toolName":"search_policies","arguments":{"regionId":998},"dependsOn":[]}
                         ],
                         "comparisonDimensions":[],
                         "outputSections":["directAnswer","keyFindings","caseInsights","policyInsights","recommendations","risks","citations"]}
                        """, 12, 8, 20, 20, "plan-request", "stop"),
                new AiProviderResponse("""
                        {"action":"final","intent":"mixed_research",
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
                        "{\"ventureType\":\"solo_company\",\"regionId\":2,\"industryTagId\":7,\"stage\":\"validation\",\"budgetRange\":\"under_100k\",\"goal\":\"验证付费需求\",\"resources\":\"产品原型\"}",
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
        assertEquals(2L, executedCaseArguments.get().getRegionId());
        assertEquals(7L, executedCaseArguments.get().getIndustryTagId());
        assertEquals(2, outcome.citations().size());
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
        assertEquals(1600, requests.get(0).maxOutputTokens());
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
        assertFalse(synthesisEvidence.contains("\"caseId\""));
        assertFalse(synthesisEvidence.contains("\"policyId\""));
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
                        {"action":"evidence_insufficient","intent":"case_analysis","directAnswer":"Evidence is limited.",
                         "keyFindings":[],"caseInsights":[],"policyInsights":[],"comparison":[],"recommendations":[],
                         "risks":[],"assumptions":[],"uncertainties":[],"nextQuestions":[],
                         "citations":[{"sourceId":1,"claim":"The verified case is relevant."}],"confidence":0.4,
                         "evidenceCoverage":{"status":"insufficient","caseCount":1,"policyCount":0,"sourceCount":1,"limitations":[]}}
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
    void truncatedPlanningGetsOneCompactSchemaFreeRecoveryBeforeToolExecution() {
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
        assertEquals(3, outcome.modelRounds());
        assertEquals(1, outcome.toolCallCount());
        assertEquals(3, requests.size());
        assertTrue(requests.get(0).responseSchema() != null);
        assertTrue(requests.get(1).responseSchema() != null);
        assertTrue(requests.get(1).responseSchema().contains("\"const\":\"plan\""));
        assertTrue(requests.get(1).responseSchema().length() < requests.get(0).responseSchema().length());
        assertTrue(requests.get(1).systemPrompt().contains("compact planning recovery"));
        assertEquals(1600, requests.get(1).maxOutputTokens());
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

    private AgentRuntimeConfig config() {
        return new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan");
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
        return fixtureTool(name, type, schema, objectMapper, sourceId, caseId, null);
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
        return new AgentTool<>() {
            public String name() { return name; }
            public String description() { return "fixture " + name; }
            public Class<T> argumentType() { return type; }
            public String argumentSchema() { return schema; }
            public AgentToolResult execute(AgentToolContext context, T arguments) {
                if (capturedArguments != null) capturedArguments.set(arguments);
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
