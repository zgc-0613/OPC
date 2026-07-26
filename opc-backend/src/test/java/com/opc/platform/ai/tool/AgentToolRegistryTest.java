package com.opc.platform.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.contract.AgentResearchContract;
import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.mapper.AgentEvidenceToolMapper;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolRegistryTest {

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

        assertEquals("FORBIDDEN_REGION_ID", exception.getDiagnosticCode());
        verify(evidenceMapper, never()).selectDescendantRegionIds(any());
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
                .path("properties");

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
        assertEquals(AgentResearchContract.MAX_DEPENDENCIES,
                properties.path("toolRequests").path("items").path("properties")
                        .path("dependsOn").path("maxItems").asInt());
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
                .path("oneOf").get(0).path("properties");
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
        verify(evidenceMapper, never()).searchCases(any(), any(), any(), any(), any(), any(Integer.class));
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
        verify(evidenceMapper, never()).searchCases(any(), any(), any(), any(), any(), any(Integer.class));
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
}
