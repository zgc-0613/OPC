package com.opc.platform.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.mapper.AgentEvidenceToolMapper;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolRegistryTest {

    @Test
    void multiRoundSchemasExposeOnlyRuntimeValidToolDependencyShapes() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool<?> searchCases = schemaOnlyTool("search_cases");
        AgentTool<?> searchPolicies = schemaOnlyTool("search_policies");
        AgentTool<?> compareCases = schemaOnlyTool("compare_cases");
        AgentTool<?> getSource = schemaOnlyTool("get_source");
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(searchCases, searchPolicies, compareCases, getSource), objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), mock(AiAgentToolCallMapper.class));

        var planningRequestBranches = objectMapper.readTree(registry.jsonResearchPlanSchemaV2())
                .path("oneOf").get(0).path("properties").path("toolRequests")
                .path("items").path("oneOf");
        Set<String> planningTools = new java.util.LinkedHashSet<>();
        planningRequestBranches.forEach(branch -> {
            planningTools.add(branch.path("properties").path("toolName").path("const").asText());
            assertEquals(0, branch.path("properties").path("dependsOn").path("maxItems").asInt());
        });
        assertEquals(Set.of("search_cases", "search_policies"), planningTools);

        assertEquals(registry.jsonResearchPlanSchemaV2(), registry.jsonCompactResearchPlanSchemaV2());

        var continuationBranches = objectMapper.readTree(registry.jsonCompactResearchFinalSchemaV2())
                .path("oneOf").get(0).path("properties").path("toolRequests")
                .path("items").path("oneOf");
        continuationBranches.forEach(branch -> {
            String tool = branch.path("properties").path("toolName").path("const").asText();
            int minimumDependencies = branch.path("properties").path("dependsOn").path("minItems").asInt();
            assertEquals(Set.of("compare_cases", "get_source").contains(tool) ? 1 : 0, minimumDependencies);
        });
    }

    @Test
    void tenBoundedChineseEvidenceItemsAreAuditedWithoutACharacterCountFalsePositive() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper mapper = mock(AiAgentToolCallMapper.class);
        AtomicReference<AiAgentToolCall> saved = new AtomicReference<>();
        when(mapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId(88L);
            return 1;
        });
        when(mapper.updateGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return 1;
        });
        AgentTool<SearchCasesArguments> largeButBounded = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "bounded Chinese evidence"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                var output = objectMapper.createObjectNode();
                var items = output.putArray("items");
                java.util.LinkedHashSet<Long> sources = new java.util.LinkedHashSet<>();
                java.util.LinkedHashSet<Long> cases = new java.util.LinkedHashSet<>();
                for (long index = 1; index <= 10; index++) {
                    long caseId = 1000 + index;
                    long sourceId = 2000 + index;
                    cases.add(caseId);
                    sources.add(sourceId);
                    items.addObject()
                            .put("caseId", caseId)
                            .put("sourceId", sourceId)
                            .put("title", "武汉人工智能创业案例" + index)
                            .put("region", "湖北".repeat(50))
                            .put("category", "科技".repeat(25))
                            .put("summary", "已核验的中文案例摘要".repeat(50))
                            .put("businessModel", "小预算服务验证路径".repeat(55))
                            .put("outcome", "形成可核验交付结果".repeat(55))
                            .put("matchReason", "匹配已确认地区和行业".repeat(18));
                }
                return new AgentToolResult(output, 10, "e".repeat(64), sources, cases);
            }
        };
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(largeButBounded), objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), mapper);

        AgentToolExecution execution = registry.execute(
                new AgentToolContext(91L, 42L), 1, "search_cases", objectMapper.readTree("{}"));

        assertTrue(saved.get().getResultSummaryJson().getBytes(StandardCharsets.UTF_8).length > 16_000);
        assertEquals(10, objectMapper.readTree(saved.get().getResultSummaryJson()).path("items").size());
        assertEquals(10, execution.result().output().path("totalCount").asInt());
        assertTrue(execution.result().output().path("returnedCount").asInt() < 10);
        assertEquals(true, execution.result().output().path("truncated").asBoolean());
        assertEquals(execution.result().output().path("returnedCount").asInt(), execution.result().evidenceCount());
        assertEquals(execution.result().evidenceCount(), execution.result().sourceIds().size());
        assertEquals(execution.result().evidenceCount(), execution.result().caseIds().size());
        assertEquals(false, execution.result().sourceIds().contains(2010L));
        assertEquals("completed", saved.get().getStatus());
        assertEquals(execution.result().evidenceCount(), saved.get().getEvidenceCount());
    }

    @Test
    void completedToolAuditKeepsRequestRelationshipAndSafeSearchDiagnostics() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper mapper = mock(AiAgentToolCallMapper.class);
        AtomicReference<AiAgentToolCall> saved = new AtomicReference<>();
        when(mapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId(89L);
            return 1;
        });
        when(mapper.updateGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return 1;
        });
        AtomicReference<SearchCasesArguments> received = new AtomicReference<>();
        AgentTool<SearchCasesArguments> searchCases = new AgentTool<>() {
            public String name() { return "search_cases"; }
            public String description() { return "safe audit fixture"; }
            public Class<SearchCasesArguments> argumentType() { return SearchCasesArguments.class; }
            public String argumentSchema() {
                return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}";
            }
            public AgentToolResult execute(AgentToolContext context, SearchCasesArguments arguments) {
                received.set(arguments);
                var output = objectMapper.createObjectNode();
                output.put("returnedCount", 1);
                output.putArray("items").addObject().put("caseId", 501L).put("sourceId", 601L);
                return new AgentToolResult(output, 1, "f".repeat(64), Set.of(601L), Set.of(501L));
            }
        };
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(searchCases), objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(), mapper);
        JsonNode arguments = objectMapper.readTree("""
                {"scope":"selected","query":"private query text","category":"private category", "limit":3}
                """);

        registry.execute(
                new AgentToolContext(91L, 42L), 4, "search_cases", arguments,
                "required-search-1", List.of("planning-request"));

        assertEquals("private query text", received.get().getQuery());
        assertEquals("private category", received.get().getCategory());
        JsonNode persistedArguments = objectMapper.readTree(saved.get().getArgumentsJson());
        assertFalse(persistedArguments.toString().contains("private query text"));
        assertFalse(persistedArguments.toString().contains("private category"));
        assertTrue(persistedArguments.path("queryPresent").asBoolean());
        assertTrue(persistedArguments.path("categoryPresent").asBoolean());

        JsonNode diagnostic = objectMapper.readTree(saved.get().getResultSummaryJson()).path("_diagnostic");
        assertEquals("search_cases", diagnostic.path("toolName").asText());
        assertEquals("required-search-1", diagnostic.path("requestId").asText());
        assertEquals(4, diagnostic.path("stepNo").asInt());
        assertEquals("selected", diagnostic.path("scope").asText());
        assertTrue(diagnostic.path("queryPresent").asBoolean());
        assertTrue(diagnostic.path("categoryPresent").asBoolean());
        assertEquals(3, diagnostic.path("requestedLimit").asInt());
        assertEquals(1, diagnostic.path("returnedCount").asInt());
        assertEquals(1, diagnostic.path("distinctAuthorizedCaseCount").asInt());
        assertEquals(1, diagnostic.path("distinctAuthorizedSourceCount").asInt());
        assertEquals(List.of("planning-request"), objectMapper.convertValue(
                diagnostic.path("dependsOn"), objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class)));
        assertEquals("completed", diagnostic.path("status").asText());
    }

    @Test
    void searchRejectsAnArbitraryRegionIdOutsideTheRunAuthorization() throws Exception {
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        AgentEvidenceToolMapper evidenceMapper = mock(AgentEvidenceToolMapper.class);
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId(22L);
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(new SearchCasesTool(
                        evidenceMapper,
                        new AgentRegionResolver(mock(com.opc.platform.region.mapper.RegionMapper.class)),
                        new ObjectMapper()
                )),
                new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator(), callMapper
        );

        AgentToolException exception = assertThrows(
                AgentToolException.class,
                () -> registry.execute(
                        new AgentToolContext(91L, 42L, null, 2L),
                        1,
                        "search_cases",
                        new ObjectMapper().readTree("{\"regionId\":999,\"limit\":3}")
                )
        );

        assertEquals("INVALID_TOOL_ARGUMENTS", exception.getDiagnosticCode());
        verify(evidenceMapper, never()).selectDescendantRegionIds(any());
    }

    @Test
    void crossRegionCaseSearchExcludesTheConfirmedRegionTreeAndKeepsTheProfileIndustry() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        AgentEvidenceToolMapper evidenceMapper = mock(AgentEvidenceToolMapper.class);
        RegionMapper regionMapper = mock(RegionMapper.class);
        Region selected = new Region();
        selected.setId(2L);
        selected.setLevel("province");
        when(regionMapper.selectById(2L)).thenReturn(selected);
        when(evidenceMapper.selectDescendantRegionIds(2L)).thenReturn(List.of(2L, 3L));
        when(evidenceMapper.searchCases(any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of());
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            invocation.<AiAgentToolCall>getArgument(0).setId(23L);
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(new SearchCasesTool(
                        evidenceMapper, new AgentRegionResolver(regionMapper), objectMapper)),
                objectMapper, Validation.buildDefaultValidatorFactory().getValidator(), callMapper
        );

        registry.execute(
                new AgentToolContext(91L, 42L, null, 2L, 7L, "AI"),
                1,
                "search_cases",
                objectMapper.readTree("{\"scope\":\"cross_region_reference\",\"limit\":3}")
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> included = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> excluded = ArgumentCaptor.forClass(List.class);
        verify(evidenceMapper).searchCases(
                included.capture(), excluded.capture(),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("AI"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(3)
        );
        assertEquals(List.of(), included.getValue());
        assertEquals(List.of(2L, 3L), excluded.getValue());
    }

    @Test
    void researchPlanSchemaAllowsABoundedUniqueRelevantSectionSet() throws Exception {
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(), new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator(),
                mock(AiAgentToolCallMapper.class)
        );

        var schema = new ObjectMapper().readTree(registry.jsonResearchPlanSchemaV2());
        var properties = schema.path("oneOf").get(0).path("properties");
        var outputSections = properties.path("outputSections");

        assertEquals(2, outputSections.path("minItems").asInt());
        assertEquals(AgentResearchContract.OUTPUT_SECTIONS.size(), outputSections.path("maxItems").asInt());
        assertEquals(true, outputSections.path("uniqueItems").asBoolean());
        assertEquals(AgentResearchContract.MAX_PLANNED_TOOLS,
                properties.path("toolRequests").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_RESEARCH_QUESTIONS,
                properties.path("researchQuestions").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_RESEARCH_QUESTION_LENGTH,
                properties.path("researchQuestions").path("items").path("maxLength").asInt());
        assertEquals(AgentResearchContract.MAX_COMPARISON_DIMENSIONS,
                properties.path("comparisonDimensions").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_COMPARISON_DIMENSION_LENGTH,
                properties.path("comparisonDimensions").path("items").path("maxLength").asInt());
    }

    @Test
    void compactPlanningSchemaUsesTheSameCanonicalLimitsAsJavaValidation() throws Exception {
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(), new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator(),
                mock(AiAgentToolCallMapper.class)
        );

        var properties = new ObjectMapper().readTree(registry.jsonCompactResearchPlanSchemaV2())
                .path("oneOf").get(0).path("properties");

        assertEquals(AgentResearchContract.MAX_RESEARCH_QUESTIONS,
                properties.path("researchQuestions").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_RESEARCH_QUESTION_LENGTH,
                properties.path("researchQuestions").path("items").path("maxLength").asInt());
        assertEquals(AgentResearchContract.MAX_PLANNED_TOOLS,
                properties.path("toolRequests").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_COMPARISON_DIMENSIONS,
                properties.path("comparisonDimensions").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_COMPARISON_DIMENSION_LENGTH,
                properties.path("comparisonDimensions").path("items").path("maxLength").asInt());
        properties.path("toolRequests").path("items").path("oneOf").forEach(request ->
                assertEquals(0, request.path("properties").path("dependsOn").path("maxItems").asInt()));
        assertEquals(AgentResearchContract.OUTPUT_SECTIONS.size(),
                properties.path("outputSections").path("maxItems").asInt());
    }

    @Test
    void compactSynthesisSchemaUsesTheSameCanonicalLimitsAsJavaValidation() throws Exception {
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(), new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator(),
                mock(AiAgentToolCallMapper.class)
        );

        var properties = new ObjectMapper().readTree(registry.jsonCompactResearchFinalSchemaV2())
                .path("oneOf").get(1).path("properties");
        var fact = properties.path("keyFindings").path("items").path("oneOf")
                .get(0).path("properties");

        assertEquals(AgentResearchContract.MAX_DIRECT_ANSWER_LENGTH,
                properties.path("directAnswer").path("maxLength").asInt());
        assertEquals(AgentResearchContract.MAX_KEY_FINDINGS,
                properties.path("keyFindings").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_COMPARISON_ITEMS,
                properties.path("comparison").path("maxItems").asInt());
        assertEquals(1, fact.path("sourceIds").path("minItems").asInt());
        assertEquals(AgentResearchContract.MAX_RECOMMENDATIONS,
                properties.path("recommendations").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_CITATIONS,
                properties.path("citations").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_COVERAGE_LIMITATIONS,
                properties.path("evidenceCoverage").path("properties")
                        .path("limitations").path("maxItems").asInt());
    }

    @Test
    void compactRecoverySchemaHasABoundedSinglePassOutput() throws Exception {
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(), new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator(),
                mock(AiAgentToolCallMapper.class)
        );

        var properties = new ObjectMapper()
                .readTree(registry.jsonCompactResearchRecoverySchemaV2(Set.of(41L, 42L, 43L, 44L)))
                .path("oneOf").get(0).path("properties");
        var statementBranches = properties.path("keyFindings").path("items").path("oneOf");
        var recommendation = properties.path("recommendations").path("items").path("properties");
        var coverageLimitations = properties.path("evidenceCoverage").path("properties").path("limitations");

        assertEquals(9, properties.size());
        assertTrue(properties.has("verificationClaims"));
        assertEquals(300, properties.path("directAnswer").path("maxLength").asInt());
        assertEquals(1, properties.path("keyFindings").path("maxItems").asInt());
        statementBranches.forEach(branch -> {
            assertEquals(240, branch.path("properties").path("text").path("maxLength").asInt());
            assertEquals(3, branch.path("properties").path("sourceIds").path("maxItems").asInt());
        });
        assertEquals(1, properties.path("recommendations").path("maxItems").asInt());
        assertEquals(160, recommendation.path("reason").path("maxLength").asInt());
        assertEquals(160, recommendation.path("nextAction").path("maxLength").asInt());
        assertEquals(3, recommendation.path("sourceIds").path("maxItems").asInt());
        assertEquals(3, properties.path("citations").path("maxItems").asInt());
        assertEquals(120, properties.path("citations").path("items").path("properties")
                .path("claim").path("maxLength").asInt());
        assertEquals(1, coverageLimitations.path("maxItems").asInt());
        assertEquals(160, coverageLimitations.path("items").path("maxLength").asInt());
        assertFalse(properties.has("caseInsights"));
    }

    @Test
    void researchFinalSchemaRequiresEveryFactToCarryASourceId() throws Exception {
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(), new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator(),
                mock(AiAgentToolCallMapper.class)
        );

        var schema = new ObjectMapper().readTree(registry.jsonResearchFinalSchemaV2());
        var properties = schema.path("oneOf").get(0).path("properties");
        var statementBranches = properties
                .path("keyFindings").path("items").path("oneOf");

        assertEquals(3, statementBranches.size());
        var fact = statementBranches.get(0).path("properties");
        assertEquals("fact", fact.path("evidenceType").path("const").asText());
        assertEquals(1, fact.path("sourceIds").path("minItems").asInt());
        assertEquals(AgentResearchContract.MAX_KEY_FINDINGS, properties.path("keyFindings").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_CASE_INSIGHTS, properties.path("caseInsights").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_POLICY_INSIGHTS, properties.path("policyInsights").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_COMPARISON_ITEMS, properties.path("comparison").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_RISKS, properties.path("risks").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_ASSUMPTIONS, properties.path("assumptions").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_UNCERTAINTIES, properties.path("uncertainties").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_NEXT_QUESTIONS, properties.path("nextQuestions").path("maxItems").asInt());
        assertEquals(AgentResearchContract.MAX_STATEMENTS,
                AgentResearchContract.MAX_KEY_FINDINGS + AgentResearchContract.MAX_CASE_INSIGHTS
                        + AgentResearchContract.MAX_POLICY_INSIGHTS + AgentResearchContract.MAX_COMPARISON_ITEMS);
        assertEquals(AgentResearchContract.MAX_SUPPLEMENTAL_ITEMS,
                AgentResearchContract.MAX_RISKS + AgentResearchContract.MAX_ASSUMPTIONS
                        + AgentResearchContract.MAX_UNCERTAINTIES + AgentResearchContract.MAX_NEXT_QUESTIONS);
    }

    @Test
    void searchToolMetadataUsesThePublishedQueryContract() throws Exception {
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(new SearchCasesTool(mock(AgentEvidenceToolMapper.class),
                        new AgentRegionResolver(mock(com.opc.platform.region.mapper.RegionMapper.class)),
                        new ObjectMapper())),
                new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator(),
                mock(AiAgentToolCallMapper.class));

        var parameters = new ObjectMapper().readTree(registry.definitions().get(0).parametersJson());
        var jsonPlan = new ObjectMapper().readTree(registry.jsonPlanSchema());

        assertEquals(true, parameters.path("properties").has("query"));
        assertEquals(true, parameters.path("properties").has("scope"));
        assertEquals(false, parameters.path("properties").has("regionId"));
        assertEquals(false, parameters.path("properties").has("regionName"));
        assertEquals(false, parameters.path("properties").has("industryTagId"));
        assertEquals(false, parameters.path("properties").has("keywords"));
        assertEquals(true, jsonPlan.path("oneOf").get(0).path("properties")
                .path("arguments").path("properties").has("query"));
    }

    @Test
    void unknownToolArgumentFieldIsRejectedBeforeEvidenceQuery() throws Exception {
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        AgentEvidenceToolMapper evidenceMapper = mock(AgentEvidenceToolMapper.class);
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            AiAgentToolCall call = invocation.getArgument(0);
            call.setId(19L);
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        ObjectMapper objectMapper = new ObjectMapper()
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(new SearchCasesTool(evidenceMapper,
                        new AgentRegionResolver(mock(com.opc.platform.region.mapper.RegionMapper.class)),
                        objectMapper)),
                objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(),
                callMapper
        );

        AgentToolException exception = assertThrows(
                AgentToolException.class,
                () -> registry.execute(
                        new AgentToolContext(93L, 42L),
                        1,
                        "search_cases",
                        objectMapper.readTree("{\"regionId\":1,\"unknown\":true}")
                )
        );

        assertEquals("INVALID_TOOL_ARGUMENTS", exception.getDiagnosticCode());
        verify(evidenceMapper, never()).searchCases(any(), any(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void invalidToolArgumentsAreRejectedAndAuditedBeforeEvidenceQuery() throws Exception {
        AiAgentToolCallMapper callMapper = mock(AiAgentToolCallMapper.class);
        AgentEvidenceToolMapper evidenceMapper = mock(AgentEvidenceToolMapper.class);
        when(callMapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            AiAgentToolCall call = invocation.getArgument(0);
            call.setId(18L);
            return 1;
        });
        when(callMapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(new SearchCasesTool(evidenceMapper,
                        new AgentRegionResolver(mock(com.opc.platform.region.mapper.RegionMapper.class)),
                        objectMapper)),
                objectMapper,
                Validation.buildDefaultValidatorFactory().getValidator(),
                callMapper
        );

        AgentToolException exception = assertThrows(
                AgentToolException.class,
                () -> registry.execute(
                        new AgentToolContext(92L, 42L),
                        1,
                        "search_cases",
                        objectMapper.readTree("{\"limit\":11}")
                )
        );

        assertEquals("INVALID_TOOL_ARGUMENTS", exception.getDiagnosticCode());
        ArgumentCaptor<AiAgentToolCall> captor = ArgumentCaptor.forClass(AiAgentToolCall.class);
        verify(callMapper).insertGuarded(captor.capture(), any());
        verify(callMapper).updateGuarded(captor.getValue(), null);
        verify(evidenceMapper, never()).searchCases(any(), any(), any(), any(), any(), any(), any(Integer.class));
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals("INVALID_TOOL_ARGUMENTS", captor.getValue().getDiagnosticCode());
    }

    @Test
    void unknownToolIsRejectedAndAuditedWithoutExecutingAnything() throws Exception {
        AiAgentToolCallMapper mapper = mock(AiAgentToolCallMapper.class);
        when(mapper.insertGuarded(any(AiAgentToolCall.class), any())).thenAnswer(invocation -> {
            AiAgentToolCall call = invocation.getArgument(0);
            call.setId(17L);
            return 1;
        });
        when(mapper.updateGuarded(any(AiAgentToolCall.class), any())).thenReturn(1);
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(), new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator(), mapper
        );

        AgentToolException exception = assertThrows(
                AgentToolException.class,
                () -> registry.execute(
                        new AgentToolContext(91L, 42L),
                        1,
                        "delete_database",
                        new ObjectMapper().readTree("{}")
                )
        );

        assertEquals("UNKNOWN_TOOL", exception.getDiagnosticCode());
        ArgumentCaptor<AiAgentToolCall> captor = ArgumentCaptor.forClass(AiAgentToolCall.class);
        verify(mapper).insertGuarded(captor.capture(), any());
        verify(mapper).updateGuarded(captor.getValue(), null);
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals("UNKNOWN_TOOL", captor.getValue().getDiagnosticCode());
        assertEquals("delete_database", captor.getValue().getToolName());
    }

    @Test
    void dependencyAuthorizationIsScopedToTheNamedRequestForCasesAndSources() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentToolContext context = new AgentToolContext(91L, 42L);
        context.registerRequestResult("requestA", "search_cases", new AgentToolResult(
                objectMapper.valueToTree(java.util.Map.of("items", List.of(
                        java.util.Map.of("caseId", 11L, "sourceId", 101L),
                        java.util.Map.of("caseId", 12L, "sourceId", 102L)
                ))), 2, "a".repeat(64), Set.of(101L, 102L), Set.of(11L, 12L)));
        context.registerRequestResult("requestB", "search_cases", new AgentToolResult(
                objectMapper.valueToTree(java.util.Map.of("items", List.of(
                        java.util.Map.of("caseId", 21L, "sourceId", 201L),
                        java.util.Map.of("caseId", 22L, "sourceId", 202L)
                ))), 2, "b".repeat(64), Set.of(201L, 202L), Set.of(21L, 22L)));

        assertTrue(context.dependenciesAuthorize(
                "compare_cases", objectMapper.valueToTree(java.util.Map.of("caseIds", List.of(11L, 12L))),
                List.of("requestA")));
        assertFalse(context.dependenciesAuthorize(
                "compare_cases", objectMapper.valueToTree(java.util.Map.of("caseIds", List.of(21L, 22L))),
                List.of("requestA")));
        assertTrue(context.dependenciesAuthorize(
                "get_source", objectMapper.valueToTree(java.util.Map.of("sourceId", 101L)),
                List.of("requestA")));
        assertFalse(context.dependenciesAuthorize(
                "get_source", objectMapper.valueToTree(java.util.Map.of("sourceId", 201L)),
                List.of("requestA")));
    }

    @Test
    void synthesisSchemaRestrictsEverySourceIdentityToTheCurrentRunAllowlist() throws Exception {
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(schemaOnlyTool("search_cases")), new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator(),
                mock(AiAgentToolCallMapper.class));

        JsonNode schema = new ObjectMapper().readTree(
                registry.jsonCompactResearchFinalSchemaV2(2, Set.of(41L, 42L)));
        JsonNode finalBranch = java.util.stream.StreamSupport.stream(
                        schema.path("oneOf").spliterator(), false)
                .filter(branch -> "final".equals(branch.path("properties")
                        .path("action").path("const").asText()))
                .findFirst().orElseThrow();
        JsonNode properties = finalBranch.path("properties");

        assertEquals(Set.of(41L, 42L), longValues(properties.path("citations")
                .path("items").path("properties").path("sourceId").path("enum")));
        properties.path("keyFindings").path("items").path("oneOf").forEach(statement ->
                assertEquals(Set.of(41L, 42L), longValues(statement
                        .path("properties").path("sourceIds").path("items").path("enum"))));
        assertEquals(Set.of(41L, 42L), longValues(properties.path("recommendations")
                .path("items").path("properties").path("sourceIds")
                .path("items").path("enum")));
    }

    private Set<Long> longValues(JsonNode values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .map(JsonNode::asLong)
                .collect(java.util.stream.Collectors.toSet());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private AgentTool<?> schemaOnlyTool(String name) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.argumentSchema()).thenReturn(
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{}}");
        return tool;
    }
}
