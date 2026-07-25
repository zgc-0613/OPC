package com.opc.platform.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    void searchToolMetadataUsesThePublishedQueryContract() throws Exception {
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(new SearchCasesTool(mock(AgentEvidenceToolMapper.class), new ObjectMapper())),
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
        when(callMapper.insert(any(AiAgentToolCall.class))).thenAnswer(invocation -> {
            AiAgentToolCall call = invocation.getArgument(0);
            call.setId(19L);
            return 1;
        });
        ObjectMapper objectMapper = new ObjectMapper()
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(new SearchCasesTool(evidenceMapper, objectMapper)),
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
        when(callMapper.insert(any(AiAgentToolCall.class))).thenAnswer(invocation -> {
            AiAgentToolCall call = invocation.getArgument(0);
            call.setId(18L);
            return 1;
        });
        ObjectMapper objectMapper = new ObjectMapper();
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(new SearchCasesTool(evidenceMapper, objectMapper)),
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
        verify(callMapper).insert(captor.capture());
        verify(callMapper).updateById(captor.getValue());
        verify(evidenceMapper, never()).searchCases(any(), any(), any(), any(), any(), any(Integer.class));
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals("INVALID_TOOL_ARGUMENTS", captor.getValue().getDiagnosticCode());
    }

    @Test
    void unknownToolIsRejectedAndAuditedWithoutExecutingAnything() throws Exception {
        AiAgentToolCallMapper mapper = mock(AiAgentToolCallMapper.class);
        when(mapper.insert(any(AiAgentToolCall.class))).thenAnswer(invocation -> {
            AiAgentToolCall call = invocation.getArgument(0);
            call.setId(17L);
            return 1;
        });
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
        verify(mapper).insert(captor.capture());
        verify(mapper).updateById(captor.getValue());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals("UNKNOWN_TOOL", captor.getValue().getDiagnosticCode());
        assertEquals("delete_database", captor.getValue().getToolName());
    }
}
