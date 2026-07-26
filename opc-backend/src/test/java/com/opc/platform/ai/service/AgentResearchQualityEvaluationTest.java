package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AiProviderMessage;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.tool.AgentTool;
import com.opc.platform.ai.tool.AgentToolContext;
import com.opc.platform.ai.tool.AgentToolRegistry;
import com.opc.platform.ai.tool.AgentToolResult;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentResearchQualityEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deterministicResearchQualityScenariosMeetEvidenceAndProfileGates() throws Exception {
        List<Scenario> scenarios = List.of(
                new Scenario("hubei-ai-policy", "policy_lookup", List.of("search_policies"), "under_100k", "validation", "partial", false, false),
                new Scenario("wuhan-case-search", "case_analysis", List.of("search_cases"), "under_100k", "validation", "partial", false, false),
                new Scenario("compare-two-cases", "case_comparison", List.of("search_cases", "compare_cases"), "100k_500k", "validation", "partial", false, false),
                new Scenario("technology-path", "technology_assessment", List.of("search_cases"), "under_100k", "idea", "partial", false, false),
                new Scenario("verify-source", "source_verification", List.of("search_policies", "get_source"), "under_100k", "validation", "partial", false, false),
                new Scenario("insufficient-local-data", "case_analysis", List.of("search_cases"), "under_100k", "validation", "insufficient", true, false),
                new Scenario("cross-region-reference", "case_comparison", List.of("search_cases", "search_cases"), "100k_500k", "growth", "partial", false, false),
                new Scenario("follow-up", "follow_up", List.of("search_policies"), "under_100k", "validation", "partial", false, true),
                new Scenario("same-goal-low-budget", "mixed_research", List.of("search_cases", "search_policies"), "under_100k", "validation", "sufficient", false, false),
                new Scenario("same-goal-growth-stage", "mixed_research", List.of("search_cases", "search_policies"), "100k_500k", "growth", "sufficient", false, false)
        );
        Map<String, String> answers = new LinkedHashMap<>();
        int validCitations = 0;
        int citations = 0;

        for (Scenario scenario : scenarios) {
            Evaluation evaluation = evaluate(scenario);
            AgentOrchestratorOutcome outcome = evaluation.outcome();
            JsonNode structured = outcome.structuredResult();
            assertEquals(scenario.intent(), structured.path("intent").asText(), scenario.id());
            assertEquals(scenario.insufficient() ? "evidence_insufficient" : "completed", outcome.status(), scenario.id());
            assertEquals(scenario.coverage(), structured.path("evidenceCoverage").path("status").asText(), scenario.id());
            assertTrue(structured.path("evidenceCoverage").path("derivedByServer").asBoolean(), scenario.id());
            assertEquals(scenario.tools(), evaluation.toolNames(), scenario.id());
            assertEquals(2, evaluation.modelCalls(), scenario.id());
            assertRequiredSections(structured, scenario.id());
            assertEvidenceLinks(structured, evaluation.allowedSourceIds(), scenario.id());
            assertFalse(outcome.answer().contains("零售"), scenario.id());
            if (!scenario.insufficient()) {
                assertTrue(outcome.answer().contains(scenario.budget()), scenario.id() + " budget");
                assertTrue(outcome.answer().contains(scenario.stage()), scenario.id() + " stage");
                assertFalse(structured.path("recommendations").isEmpty(), scenario.id());
            }
            if ("partial".equals(scenario.coverage())) {
                assertEquals("completed", outcome.status(), scenario.id() + " partial evidence must stay useful");
            }
            if (scenario.followUp()) {
                assertTrue(evaluation.firstRequestMessages().stream()
                        .anyMatch(message -> "上一轮已确认湖北为目标地区".equals(message.content())), scenario.id());
            }
            for (AgentCitation citation : outcome.citations()) {
                citations += 1;
                if (evaluation.allowedSourceIds().contains(citation.sourceId())) validCitations += 1;
            }
            answers.put(scenario.id(), outcome.answer());
        }

        assertEquals(citations, validCitations, "legal citation rate must be 100%");
        assertNotEquals(answers.get("same-goal-low-budget"), answers.get("same-goal-growth-stage"));
    }

    private Evaluation evaluate(Scenario scenario) throws Exception {
        List<ToolAttempt> attempts = new ArrayList<>();
        AgentToolRegistry registry = registry(scenario, attempts);
        AgentOrchestrator orchestrator = new AgentOrchestrator(objectMapper, registry);
        JsonNode plan = plan(scenario);
        Set<Long> expectedSources = expectedSources(scenario);
        JsonNode result = result(scenario, expectedSources);
        ArrayDeque<AiProviderResponse> responses = new ArrayDeque<>(List.of(
                response(plan.toString(), 12, 8, "quality-plan"),
                response(result.toString(), 18, 12, "quality-result")
        ));
        AtomicInteger modelCalls = new AtomicInteger();
        List<AiProviderMessage> firstRequestMessages = new ArrayList<>();
        List<AiProviderMessage> history = scenario.followUp()
                ? List.of(AiProviderMessage.assistant("上一轮已确认湖北为目标地区")) : List.of();

        AgentOrchestratorOutcome outcome = orchestrator.execute(
                new AgentOrchestratorInput(
                        100L, 42L, profile(scenario), "研究人工智能一人公司的可行路径", history,
                        new AgentRuntimeConfig(true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan")
                ),
                request -> {
                    if (modelCalls.getAndIncrement() == 0) firstRequestMessages.addAll(request.messages());
                    return responses.removeFirst();
                },
                progress -> { }
        );
        return new Evaluation(
                outcome,
                attempts.stream().map(ToolAttempt::toolName).toList(),
                expectedSources,
                modelCalls.get(),
                List.copyOf(firstRequestMessages)
        );
    }

    private AgentToolRegistry registry(Scenario scenario, List<ToolAttempt> attempts) {
        AiAgentToolCallMapper mapper = mock(AiAgentToolCallMapper.class);
        AtomicInteger ids = new AtomicInteger();
        when(mapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            AiAgentToolCall call = invocation.getArgument(0);
            call.setId((long) ids.incrementAndGet());
            attempts.add(new ToolAttempt(call.getToolName(), objectMapper.readTree(call.getArgumentsJson())));
            return 1;
        });
        when(mapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        return new AgentToolRegistry(
                List.of(
                        qualityTool("search_cases", scenario), qualityTool("search_policies", scenario),
                        qualityTool("compare_cases", scenario), qualityTool("get_source", scenario)
                ),
                objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(),
                mapper
        );
    }

    private AgentTool<Map<String, Object>> qualityTool(String name, Scenario scenario) {
        return new AgentTool<>() {
            public String name() { return name; }
            public String description() { return "deterministic quality evidence"; }
            @SuppressWarnings("unchecked")
            public Class<Map<String, Object>> argumentType() { return (Class<Map<String, Object>>) (Class<?>) Map.class; }
            public String argumentSchema() {
                return switch (name) {
                    case "compare_cases" -> "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"caseIds\"],\"properties\":{\"caseIds\":{\"type\":\"array\",\"minItems\":2,\"maxItems\":3,\"items\":{\"type\":\"integer\"}}}}";
                    case "get_source" -> "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"sourceId\"],\"properties\":{\"sourceId\":{\"type\":\"integer\"}}}";
                    default -> "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"regionId\",\"industryTagId\"],\"properties\":{\"regionId\":{\"type\":\"integer\"},\"industryTagId\":{\"type\":\"integer\"}}}";
                };
            }
            public AgentToolResult execute(AgentToolContext context, Map<String, Object> arguments) {
                if (scenario.insufficient()) return result(name, Set.of(), Set.of(), objectMapper.createObjectNode().putArray("items"));
                return switch (name) {
                    case "search_cases" -> result(name, Set.of(1L, 3L), Set.of(11L, 12L), caseOutput(arguments));
                    case "search_policies" -> result(name, Set.of(2L), Set.of(), policyOutput());
                    case "compare_cases" -> {
                        if (!context.allowedCaseIds().containsAll(Set.of(11L, 12L))) throw new AssertionError("compare dependency not authorized");
                        yield result(name, Set.of(1L, 3L), Set.of(11L, 12L), objectMapper.createObjectNode().putArray("items"));
                    }
                    default -> {
                        if (!context.allowedSourceIds().contains(2L)) throw new AssertionError("source dependency not authorized");
                        yield result(name, Set.of(2L), Set.of(), objectMapper.createObjectNode().put("sourceId", 2));
                    }
                };
            }
        };
    }

    private AgentToolResult result(
            String name,
            Set<Long> sourceIds,
            Set<Long> caseIds,
            JsonNode output
    ) {
        String hash = Integer.toHexString(name.hashCode());
        return new AgentToolResult(
                output, sourceIds.size() + caseIds.size(), (hash.repeat(64)).substring(0, 64), sourceIds, caseIds);
    }

    private JsonNode caseOutput(Map<String, Object> arguments) {
        long regionId = ((Number) arguments.get("regionId")).longValue();
        var root = objectMapper.createObjectNode();
        var items = root.putArray("items");
        items.addObject().put("caseId", 11).put("sourceId", 1)
                .put("title", regionId == 4 ? "上海人工智能服务案例" : "武汉人工智能服务案例")
                .put("region", regionId == 4 ? "上海市" : "武汉市");
        items.addObject().put("caseId", 12).put("sourceId", 3)
                .put("title", "武汉软件工作室案例").put("region", "武汉市");
        return root;
    }

    private JsonNode policyOutput() {
        var root = objectMapper.createObjectNode();
        root.putArray("items").addObject().put("policyId", 21).put("sourceId", 2)
                .put("title", "湖北创业支持政策");
        return root;
    }

    private JsonNode plan(Scenario scenario) {
        var root = objectMapper.createObjectNode();
        root.put("action", "plan").put("intent", scenario.intent());
        root.putArray("researchQuestions").add("地区与行业证据是否支持核心研究问题？");
        var requests = root.putArray("toolRequests");
        for (int index = 0; index < scenario.tools().size(); index++) {
            String tool = scenario.tools().get(index);
            var request = requests.addObject();
            request.put("requestId", "request" + index).put("toolName", tool);
            var arguments = request.putObject("arguments");
            if ("compare_cases".equals(tool)) arguments.putArray("caseIds").add(11).add(12);
            else if ("get_source".equals(tool)) arguments.put("sourceId", 2);
            else arguments.put("regionId", "cross-region-reference".equals(scenario.id()) && index == 1 ? 4 : 2)
                    .put("industryTagId", 7);
            var dependencies = request.putArray("dependsOn");
            if (Set.of("compare_cases", "get_source").contains(tool)) dependencies.add("request" + (index - 1));
        }
        root.putArray("comparisonDimensions").add("regionalContext").add("evidenceStrength");
        var sections = root.putArray("outputSections");
        for (String section : List.of(
                "directAnswer", "keyFindings", "caseInsights", "policyInsights", "comparison",
                "recommendations", "risks", "assumptions", "uncertainties", "nextQuestions",
                "citations", "confidence", "evidenceCoverage"
        )) sections.add(section);
        return root;
    }

    private JsonNode result(Scenario scenario, Set<Long> sources) {
        boolean insufficient = scenario.insufficient();
        var root = objectMapper.createObjectNode();
        root.put("action", insufficient ? "evidence_insufficient" : "final").put("intent", scenario.intent());
        root.put("directAnswer", insufficient
                ? "当前已核验证据无法支持核心事实，建议补充本地资料。"
                : "基于已核验证据，按预算 " + scenario.budget() + " 与阶段 " + scenario.stage() + " 安排验证顺序。");
        var findings = root.putArray("keyFindings");
        if (!insufficient) findings.addObject().put("text", "湖北或武汉存在相关已核验证据。")
                .put("evidenceType", "fact").putArray("sourceIds").add(sources.iterator().next());
        root.putArray("caseInsights");
        root.putArray("policyInsights");
        root.putArray("comparison");
        var recommendations = root.putArray("recommendations");
        if (!insufficient) {
            var recommendation = recommendations.addObject().put("priority", "high")
                    .put("reason", "预算 " + scenario.budget() + " 且阶段 " + scenario.stage() + " 需要控制验证成本。")
                    .put("nextAction", "两周内完成五次客户访谈并核验政策条件。");
            var sourceIds = recommendation.putArray("sourceIds");
            sources.forEach(sourceIds::add);
        }
        root.putArray("risks").add("政策条件和客户付费意愿仍需继续核验");
        root.putArray("assumptions").add("以人工智能一人公司形式起步");
        root.putArray("uncertainties").add("尚无真实付费转化数据");
        root.putArray("nextQuestions").add("首批客户属于哪个细分行业？");
        var citations = root.putArray("citations");
        if (!insufficient) sources.forEach(sourceId -> citations.addObject()
                .put("sourceId", sourceId).put("claim", "来源支持当前研究中的事实和行动建议。"));
        root.put("confidence", insufficient ? 0.2 : 0.78);
        var coverage = root.putObject("evidenceCoverage");
        coverage.put("status", scenario.coverage())
                .put("caseCount", scenario.tools().contains("search_cases") && !insufficient ? 2 : 0)
                .put("policyCount", scenario.tools().contains("search_policies") && !insufficient ? 1 : 0)
                .put("sourceCount", sources.size());
        coverage.putArray("limitations").add("缺少真实客户访谈证据");
        return root;
    }

    private String profile(Scenario scenario) {
        return "{\"ventureType\":\"solo_company\",\"regionId\":2,\"industryTagId\":7," +
                "\"industry\":\"人工智能应用\",\"stage\":\"" + scenario.stage() + "\"," +
                "\"budgetRange\":\"" + scenario.budget() + "\",\"goal\":\"验证付费需求\"," +
                "\"resources\":\"产品原型\"}";
    }

    private Set<Long> expectedSources(Scenario scenario) {
        if (scenario.insufficient()) return Set.of();
        Set<Long> sources = new LinkedHashSet<>();
        if (scenario.tools().contains("search_cases") || scenario.tools().contains("compare_cases")) {
            sources.add(1L);
            sources.add(3L);
        }
        if (scenario.tools().contains("search_policies") || scenario.tools().contains("get_source")) sources.add(2L);
        return Set.copyOf(sources);
    }

    private void assertRequiredSections(JsonNode structured, String id) {
        for (String field : List.of(
                "directAnswer", "keyFindings", "caseInsights", "policyInsights", "comparison",
                "recommendations", "risks", "assumptions", "uncertainties", "nextQuestions",
                "citations", "confidence", "evidenceCoverage"
        )) assertTrue(structured.has(field), id + " missing " + field);
    }

    private void assertEvidenceLinks(JsonNode structured, Set<Long> allowed, String id) {
        for (String field : List.of("keyFindings", "caseInsights", "policyInsights", "comparison")) {
            structured.path(field).forEach(item -> {
                if ("fact".equals(item.path("evidenceType").asText())) assertFalse(item.path("sourceIds").isEmpty(), id);
                item.path("sourceIds").forEach(source -> assertTrue(allowed.contains(source.asLong()), id));
            });
        }
        structured.path("recommendations").forEach(item -> {
            assertFalse(item.path("sourceIds").isEmpty(), id);
            item.path("sourceIds").forEach(source -> assertTrue(allowed.contains(source.asLong()), id));
        });
    }

    private AiProviderResponse response(String content, int prompt, int completion, String requestId) {
        return new AiProviderResponse(content, prompt, completion, prompt + completion, 20, requestId, "stop");
    }

    private record Scenario(
            String id,
            String intent,
            List<String> tools,
            String budget,
            String stage,
            String coverage,
            boolean insufficient,
            boolean followUp
    ) { }

    private record ToolAttempt(String toolName, JsonNode arguments) { }

    private record Evaluation(
            AgentOrchestratorOutcome outcome,
            List<String> toolNames,
            Set<Long> allowedSourceIds,
            int modelCalls,
            List<AiProviderMessage> firstRequestMessages
    ) { }
}
