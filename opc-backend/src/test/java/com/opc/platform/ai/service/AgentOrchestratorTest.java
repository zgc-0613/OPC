package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {

    @Test
    void jsonPlanExecutesWhitelistedToolThenProducesCitedFinalAnswer() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        when(callMapper.insert(any(AiAgentToolCall.class))).thenAnswer(invocation -> {
            AiAgentToolCall call = invocation.getArgument(0);
            call.setId(71L);
            return 1;
        });
        AgentTool<SearchCasesArguments> fakeSearch = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "search"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() { return "{\"type\":\"object\"}"; }
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
            public String argumentSchema() { return "{\"type\":\"object\"}"; }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                return new AgentToolResult(
                        objectMapper.valueToTree(java.util.Map.of("items", List.of())), 0,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        Set.of(), Set.of()
                );
            }
        };
        AiAgentToolCallMapper mapper = mock(AiAgentToolCallMapper.class);
        when(mapper.insert(any(AiAgentToolCall.class))).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId(1L);
            return 1;
        });
        return new AgentToolRegistry(
                List.of(search), objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), mapper
        );
    }
}
