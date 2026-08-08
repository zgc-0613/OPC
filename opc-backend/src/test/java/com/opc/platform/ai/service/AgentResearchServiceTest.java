package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.AgentMessageCreateDTO;
import com.opc.platform.ai.dto.AgentSessionStartDTO;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AgentRuntimeConfigProvider;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentResearchServiceTest {

    @Test
    void startRejectsAutoIntentWhenTaskContextDefinesTheResearchType() throws Exception {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AgentRuntimeConfigProvider configProvider = mock(AgentRuntimeConfigProvider.class);
        when(configProvider.agentRuntimeConfig()).thenReturn(new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan"));

        AgentResearchService service = new AgentResearchService(
                sessions, mock(AiAgentMessageMapper.class), mock(AiAnalysisRunMapper.class), lifecycle,
                configProvider, mock(AgentRunDispatcher.class), mock(AgentClarificationPolicy.class),
                mock(AgentProfilePolicy.class), mock(AgentSessionHistoryService.class),
                mock(TransactionTemplate.class), Runnable::run, new ObjectMapper(),
                mock(PhaseThreeSelectedEvidenceValidator.class));
        AgentSessionStartDTO request = new AgentSessionStartDTO();
        request.setContent("Compare the selected verified cases");
        request.setIdempotencyKey("idem-auto-intent-123");
        request.setRequestedIntent("auto");
        request.setTaskContext(new ObjectMapper().readTree("""
                {"version":"phase3-task-v1","taskType":"case_comparison",
                 "caseIds":[101,102],"comparisonDimensions":["businessModel"]}
                """));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.start(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), request));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("PHASE3_TASK_INTENT_MISMATCH", exception.getMessage());
        verify(sessions, never()).create(any(), any(), any(), any(), any(), any());
        verify(lifecycle, never()).enqueue(any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void analyticsStartPassesOnlyItsServerBindingIntoTheDurableRunIdentity() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AgentRuntimeConfigProvider configProvider = mock(AgentRuntimeConfigProvider.class);
        AgentClarificationPolicy clarification = mock(AgentClarificationPolicy.class);
        AgentSessionHistoryService history = mock(AgentSessionHistoryService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(configProvider.agentRuntimeConfig()).thenReturn(new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan"));
        when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        AiAgentSession session = new AiAgentSession();
        session.setId(10L); session.setUserId(42L); session.setStatus("active"); session.setContentGeneration(0L);
        when(sessions.create(any(), any(), any(), any(), any(), any())).thenReturn(session);
        when(sessions.lockOwned(any(), eq(10L))).thenReturn(session);
        when(sessions.requireOwned(any(), eq(10L))).thenReturn(session);
        AiAgentMessage message = new AiAgentMessage();
        message.setId(20L);
        when(sessions.appendMessage(any(), eq(10L), eq("user"), any(), eq("completed"), any(), any()))
                .thenReturn(message);
        when(clarification.evaluate(any(), any(), any())).thenReturn(
                new AgentClarificationDecision("{\"resolvedFields\":{},\"pendingFields\":[]}", null, false));
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(30L); run.setSessionId(10L); run.setUserMessageId(20L); run.setStatus("received");
        when(lifecycle.enqueue(any(), eq(10L), eq(20L), any(), any(), any())).thenReturn(run);
        when(messages.attachRun(20L, 30L)).thenReturn(1);
        AgentResearchService service = new AgentResearchService(
                sessions, messages, runs, lifecycle, configProvider, mock(AgentRunDispatcher.class), clarification,
                mock(AgentProfilePolicy.class), history, transactions, task -> { }, new ObjectMapper(),
                mock(PhaseThreeSelectedEvidenceValidator.class));
        com.opc.platform.ai.dto.AgentSessionStartDTO request = new com.opc.platform.ai.dto.AgentSessionStartDTO();
        request.setContent("请说明当前数据对应的研究方向");
        request.setIdempotencyKey("analytics-run-123");
        request.setRequestedIntent("general_research");
        request.setAnalyticsSnapshotBinding(new AgentAnalyticsSnapshotBinding(
                17L, "overview.verified_cases", "analytics-v1:current", "{}",
                "{\"metricId\":\"overview.verified_cases\",\"dataVersion\":\"analytics-v1:current\",\"value\":3}"));

        service.start(new AuthenticatedUser(42L, "owner", "owner@example.com"), request);

        ArgumentCaptor<AgentSubmissionIdentity> identity = ArgumentCaptor.forClass(AgentSubmissionIdentity.class);
        verify(lifecycle).enqueue(any(), eq(10L), eq(20L), eq("analytics-run-123"), any(), identity.capture());
        assertEquals(17L, identity.getValue().analyticsSnapshot().snapshotId());
        assertEquals("analytics-v1:current", identity.getValue().analyticsSnapshot().dataVersion());
    }

    @Test
    void exactStartReplayReturnsItsStoredReceiptBeforeRecheckingCurrentSelectedEvidence() throws Exception {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentRuntimeConfigProvider configProvider = mock(AgentRuntimeConfigProvider.class);
        AgentProfilePolicy profilePolicy = mock(AgentProfilePolicy.class);
        AgentSessionHistoryService history = mock(AgentSessionHistoryService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        PhaseThreeSelectedEvidenceValidator evidence = mock(PhaseThreeSelectedEvidenceValidator.class);
        when(configProvider.agentRuntimeConfig()).thenReturn(new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan"));
        when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(profilePolicy.canonicalJson(any())).thenReturn("{}");
        when(profilePolicy.fingerprint("{}")).thenReturn("profile-hash");
        PhaseThreeTaskContext context = new PhaseThreeTaskContextValidator(new ObjectMapper()).validateAndNormalize(
                new ObjectMapper().readTree("""
                        {"version":"phase3-task-v1","taskType":"case_analysis",
                         "caseIds":[101],"comparisonDimensions":[]}
                        """), "case_analysis");
        AiAnalysisRun stored = new AiAnalysisRun();
        stored.setId(30L); stored.setSessionId(10L); stored.setUserMessageId(20L); stored.setStatus("completed");
        stored.setSubmissionKind("session_start"); stored.setRequestedIntent("case_analysis");
        stored.setRequestContentHash(sha256("Analyze the selected verified case"));
        stored.setStartProfileHash("profile-hash"); stored.setSessionContentGeneration(0L);
        when(runs.findAgentByIdempotency(42L, "idem-case-replay-123")).thenReturn(stored);
        AiAgentSession session = new AiAgentSession();
        session.setId(10L); session.setStatus("active");
        session.setTaskContextVersion(PhaseThreeTaskContextValidator.VERSION);
        session.setTaskContextJson(context.canonicalJson()); session.setTaskContextHash(context.hash());
        when(sessions.requireOwned(any(), eq(10L))).thenReturn(session);

        AgentResearchService service = new AgentResearchService(
                sessions, messages, runs, mock(AgentRunLifecycleService.class), configProvider,
                mock(AgentRunDispatcher.class), mock(AgentClarificationPolicy.class), profilePolicy,
                history, transactions, Runnable::run, new ObjectMapper(), evidence);
        com.opc.platform.ai.dto.AgentSessionStartDTO request = new com.opc.platform.ai.dto.AgentSessionStartDTO();
        request.setContent("Analyze the selected verified case");
        request.setIdempotencyKey("idem-case-replay-123");
        request.setRequestedIntent("case_analysis");
        request.setTaskContext(context.node());

        AgentResearchStartReceipt replay = service.start(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), request);

        assertEquals(30L, replay.runId());
        assertEquals("completed", replay.status());
        assertEquals("case_analysis", replay.taskType());
        assertEquals(context.hash(), replay.taskContextHash());
        assertEquals("case_analysis", replay.taskContext().path("taskType").asText());
        verify(evidence, never()).validate(any());
        verify(sessions, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void newResearchRejectsIneligibleSelectedEvidenceBeforeSessionMessageRunOrTokenCreation() throws Exception {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AgentRuntimeConfigProvider configProvider = mock(AgentRuntimeConfigProvider.class);
        AgentProfilePolicy profilePolicy = mock(AgentProfilePolicy.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(configProvider.agentRuntimeConfig()).thenReturn(new AgentRuntimeConfig(
                true, 4, 6, 8000, 12, Duration.ofSeconds(120), "json_plan"));
        when(transactions.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        CaseItemMapper cases = mock(CaseItemMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        when(cases.selectByIdForUpdate(101L)).thenReturn(null);

        AgentResearchService service = new AgentResearchService(
                sessions, messages, runs, lifecycle, configProvider, mock(AgentRunDispatcher.class),
                mock(AgentClarificationPolicy.class), profilePolicy, mock(AgentSessionHistoryService.class),
                transactions, Runnable::run, new ObjectMapper(),
                new PhaseThreeSelectedEvidenceValidator(cases, sources));
        com.opc.platform.ai.dto.AgentSessionStartDTO request = new com.opc.platform.ai.dto.AgentSessionStartDTO();
        request.setContent("Analyze the selected verified case");
        request.setIdempotencyKey("idem-case-check-123");
        request.setRequestedIntent("case_analysis");
        request.setTaskContext(new ObjectMapper().readTree("""
                {"version":"phase3-task-v1","taskType":"case_analysis",
                 "caseIds":[101],"comparisonDimensions":[]}
                """));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.start(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), request));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("PHASE3_CASE_NOT_ELIGIBLE", exception.getMessage());
        verify(sessions, never()).create(any(), any(), any(), any(), any(), any());
        verify(sessions, never()).appendMessage(any(), anyLong(), any(), any(), any(), any(), any());
        verify(lifecycle, never()).enqueue(any(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void explicitBoundaryChangeIsRejectedBeforeMessageWriteOrTokenReservation() {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AgentRunLifecycleService lifecycle = mock(AgentRunLifecycleService.class);
        AgentRuntimeConfigProvider configProvider = mock(AgentRuntimeConfigProvider.class);
        AgentRunDispatcher dispatcher = mock(AgentRunDispatcher.class);
        AgentClarificationPolicy clarification = mock(AgentClarificationPolicy.class);
        AgentProfilePolicy profilePolicy = mock(AgentProfilePolicy.class);
        AgentSessionHistoryService historyService = mock(AgentSessionHistoryService.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
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
        session.setProfileJson("{\"regionId\":2,\"industryTagId\":7}");
        when(sessions.lockOwned(any(), anyLong())).thenReturn(session);
        when(clarification.changesResearchBoundary(any(), any())).thenReturn(true);
        AgentResearchService service = new AgentResearchService(
                sessions, messages, runs, lifecycle, configProvider, dispatcher, clarification,
                profilePolicy, historyService, transactions, Runnable::run, new ObjectMapper(),
                mock(PhaseThreeSelectedEvidenceValidator.class));
        AgentMessageCreateDTO request = new AgentMessageCreateDTO();
        request.setContent("请把研究地区改为广东省");
        request.setIdempotencyKey("idem-boundary-123");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.submit(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), 10L, request));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("需要基于新条件创建研究", exception.getMessage());
        verify(sessions, never()).appendMessage(any(), anyLong(), any(), any(), any(), any(), any());
        verify(lifecycle, never()).enqueue(any(), anyLong(), anyLong(), any(), any(), any());
        verify(messages, never()).attachRun(anyLong(), anyLong());
    }

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
                profilePolicy, historyService, transactions, rejectingExecutor, objectMapper,
                mock(PhaseThreeSelectedEvidenceValidator.class));
        AgentMessageCreateDTO request = new AgentMessageCreateDTO();
        request.setContent("Research Hubei AI opportunities");
        request.setIdempotencyKey("idem-queue-123");
        request.setRequestedIntent("case_comparison");

        AgentResearchReceipt receipt = service.submit(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), 10L, request);

        assertEquals("received", receipt.status());
        assertEquals(30L, receipt.runId());
        ArgumentCaptor<AgentSubmissionIdentity> identity = ArgumentCaptor.forClass(AgentSubmissionIdentity.class);
        verify(lifecycle).enqueue(any(), anyLong(), anyLong(), any(), any(), identity.capture());
        assertEquals("case_comparison", identity.getValue().requestedIntent());
        verify(sessions).updateResearchContext(any(), anyLong(), any());
        verify(dispatcher, never()).processNext();
    }

    @Test
    void followUpMessageCannotReplaceTheSessionTaskContext() throws Exception {
        AgentMessageCreateDTO request = new AgentMessageCreateDTO();
        request.setContent("Use another boundary for this follow-up");
        request.setIdempotencyKey("idem-context-12345");
        request.setTaskContext(new ObjectMapper().readTree("""
                {"version":"phase3-task-v1","taskType":"general_research",
                 "caseIds":[],"comparisonDimensions":[]}
                """));

        AgentResearchService service = new AgentResearchService(
                mock(AgentSessionService.class), mock(AiAgentMessageMapper.class),
                mock(AiAnalysisRunMapper.class), mock(AgentRunLifecycleService.class),
                mock(AgentRuntimeConfigProvider.class), mock(AgentRunDispatcher.class),
                mock(AgentClarificationPolicy.class), mock(AgentProfilePolicy.class),
                mock(AgentSessionHistoryService.class), mock(TransactionTemplate.class),
                Runnable::run, new ObjectMapper(), mock(PhaseThreeSelectedEvidenceValidator.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.submit(
                new AuthenticatedUser(42L, "owner", "owner@example.com"), 10L, request));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
