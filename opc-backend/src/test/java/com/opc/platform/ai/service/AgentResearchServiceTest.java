package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.AgentMessageCreateDTO;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AgentRuntimeConfigProvider;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentResearchServiceTest {

    @Test
    void rejectedWakeupLeavesReceivedRunAvailableForDatabaseWorker() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AgentRuntimeConfigProvider configProvider = mock(AgentRuntimeConfigProvider.class);
        AgentRunDispatcher dispatcher = mock(AgentRunDispatcher.class);
        AgentClarificationPolicy clarification = mock(AgentClarificationPolicy.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentProfilePolicy profilePolicy = new AgentProfilePolicy(
                objectMapper,
                mock(com.opc.platform.region.mapper.RegionMapper.class),
                mock(com.opc.platform.tag.mapper.TagMapper.class));
        AgentSessionHistoryService historyService = mock(AgentSessionHistoryService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        TaskExecutor rejectingExecutor = task -> { throw new TaskRejectedException("queue full"); };
        AgentRuntimeConfig config = new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan");
        when(configProvider.agentRuntimeConfig()).thenReturn(config);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        AiAgentSession session = new AiAgentSession();
        session.setId(10L);
        session.setUserId(42L);
        session.setStatus("active");
        session.setProfileJson("{\"regionId\":1,\"industry\":\"AI\"}");
        when(sessions.requireOwned(any(), anyLong())).thenReturn(session);
        when(sessions.lockOwned(any(), anyLong())).thenReturn(session);
        AiAgentMessage userMessage = new AiAgentMessage();
        userMessage.setId(20L);
        when(sessions.appendMessage(any(), anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(userMessage);
        when(clarification.evaluate(any(), any(), any())).thenReturn(
                new AgentClarificationDecision(
                        "{\"resolvedFields\":{\"regionId\":1,\"industryTagId\":2},\"pendingFields\":[]}",
                        null,
                        false
                ));
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(30L);
        run.setSessionId(10L);
        run.setUserMessageId(20L);
        run.setStatus("received");
        when(lifecycle.enqueue(any(), anyLong(), anyLong(), any(), any(), any())).thenReturn(run);
        when(messages.attachRun(20L, 30L)).thenReturn(1);
        AgentResearchService service = new AgentResearchService(
                sessions, messages, runs, lifecycle, configProvider, dispatcher, clarification,
                profilePolicy, historyService, transactions, rejectingExecutor, objectMapper);
        AgentMessageCreateDTO request = new AgentMessageCreateDTO();
        request.setContent("Research Hubei AI opportunities");
        request.setIdempotencyKey("idem-queue-123");

        AgentResearchReceipt receipt = service.submit(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), 10L, request);

        assertEquals("received", receipt.status());
        assertEquals(30L, receipt.runId());
        verify(lifecycle).enqueue(any(), anyLong(), anyLong(), any(), any(), any());
        verify(sessions).updateResearchContext(any(), anyLong(), any());
        verify(dispatcher, never()).processNext();
    }
}
