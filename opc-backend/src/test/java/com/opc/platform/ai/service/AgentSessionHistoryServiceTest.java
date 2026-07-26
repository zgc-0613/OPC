package com.opc.platform.ai.service;

import com.opc.platform.ai.dto.AgentSessionUpdateDTO;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.exception.AgentHistoryCursorStaleException;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAgentSessionMapper;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.ai.vo.AgentMessagePageVO;
import com.opc.platform.ai.vo.AgentSessionHistoryPageVO;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSessionHistoryServiceTest {

    private final AuthenticatedUser user = new AuthenticatedUser(42L, "owner", "owner@example.com");
    private AiAgentSessionMapper sessions;
    private AiAgentMessageMapper messages;
    private AiAnalysisRunMapper runs;
    private AiAgentToolCallMapper tools;
    private AiRuntimeSettingsProvider settings;
    private AgentSessionHistoryService service;

    @BeforeEach
    void setUp() {
        sessions = mock(AiAgentSessionMapper.class);
        messages = mock(AiAgentMessageMapper.class);
        runs = mock(AiAnalysisRunMapper.class);
        tools = mock(AiAgentToolCallMapper.class);
        settings = mock(AiRuntimeSettingsProvider.class);
        when(sessions.incrementHistoryRevision(any())).thenReturn(1);
        service = new AgentSessionHistoryService(
                sessions, messages, runs, tools, settings, new ObjectMapper(),
                "unit-test-history-cursor-secret-1234567890",
                mock(AgentContentPurgeAuditService.class));
    }

    @Test
    void environmentCursorSecretWorksWithoutExternalYamlMapping() {
        AiAgentSessionMapper contextSessions = mock(AiAgentSessionMapper.class);
        LocalDateTime now = LocalDateTime.of(2026, 7, 26, 1, 55);
        AiAgentSession first = activeSession();
        first.setCreatedAt(now.minusMinutes(1));
        AiAgentSession second = activeSession();
        second.setId(9L);
        second.setCreatedAt(now.minusMinutes(2));
        when(contextSessions.selectCurrentTimestamp()).thenReturn(now);
        when(contextSessions.selectHistory(eq(42L), eq("active"), eq(null), eq(now),
                eq(null), eq(null), eq(null), eq(2))).thenReturn(List.of(first, second));

        new ApplicationContextRunner()
                .withPropertyValues(
                        "OPC_ASSISTANT_CURSOR_HMAC_SECRET=environment-cursor-secret-1234567890")
                .withBean(AiAgentSessionMapper.class, () -> contextSessions)
                .withBean(AiAgentMessageMapper.class, () -> mock(AiAgentMessageMapper.class))
                .withBean(AiAnalysisRunMapper.class, () -> mock(AiAnalysisRunMapper.class))
                .withBean(AiAgentToolCallMapper.class, () -> mock(AiAgentToolCallMapper.class))
                .withBean(AiRuntimeSettingsProvider.class, () -> mock(AiRuntimeSettingsProvider.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(AgentContentPurgeAuditService.class,
                        () -> mock(AgentContentPurgeAuditService.class))
                .withBean(AgentSessionHistoryService.class)
                .run(context -> {
                    AgentSessionHistoryPageVO page = context.getBean(AgentSessionHistoryService.class)
                            .history(user, "active", "", null, 1);
                    assertNotNull(page.nextCursor());
                });
    }

    @Test
    void manualRenameMarksTitleAsManualAndPreventsLaterAutoTitle() {
        AiAgentSession session = activeSession();
        session.setTitleMode("auto");
        when(sessions.selectOwnedForUpdate(10L, 42L)).thenReturn(session);
        when(sessions.updateById(any(AiAgentSession.class))).thenReturn(1);
        AgentSessionUpdateDTO request = new AgentSessionUpdateDTO();
        request.setTitle("湖北人工智能创业机会");

        service.update(user, 10L, request);

        assertEquals("湖北人工智能创业机会", session.getTitle());
        assertEquals("manual", session.getTitleMode());
        verify(sessions).updateById(session);
    }

    @Test
    void trashAndRestorePreserveThePreviousArchivedState() {
        AiAgentSession session = activeSession();
        session.setStatus("archived");
        session.setArchivedAt(LocalDateTime.now().minusDays(1));
        when(sessions.selectOwnedForUpdate(10L, 42L)).thenReturn(session);
        when(sessions.updateById(any(AiAgentSession.class))).thenReturn(1);
        when(runs.countNonTerminalForSession(10L)).thenReturn(0);

        service.trash(user, 10L);
        assertEquals("archived", session.getStatus());
        service.restore(user, 10L);

        assertEquals("archived", session.getStatus());
        assertNull(session.getDeletedAt());
        assertNull(session.getPurgeAfter());
    }

    @Test
    void runningSessionCannotBeMovedToTrash() {
        AiAgentSession session = activeSession();
        when(sessions.selectOwnedForUpdate(10L, 42L)).thenReturn(session);
        when(runs.countNonTerminalForSession(10L)).thenReturn(1);

        assertThrows(BusinessException.class, () -> service.trash(user, 10L));

        verify(sessions, never()).updateById(any(AiAgentSession.class));
    }

    @Test
    void permanentPurgeScrubsConversationContentButKeepsRunAccountingRows() {
        AiAgentSession session = activeSession();
        session.setDeletedAt(LocalDateTime.now().minusDays(31));
        session.setPurgeAfter(LocalDateTime.now().minusMinutes(1));
        when(sessions.selectOwnedForUpdate(10L, 42L)).thenReturn(session);
        when(runs.countNonTerminalForSession(10L)).thenReturn(0);
        when(messages.purgeSessionContent(10L)).thenReturn(4);
        when(tools.purgeSessionContent(10L)).thenReturn(2);
        when(runs.purgeSessionContent(10L)).thenReturn(1);
        when(sessions.purgeSessionContent(eq(10L), eq("[已删除]"), eq("manual"), any(LocalDateTime.class)))
                .thenReturn(1);

        service.purge(user, 10L);

        assertEquals("[已删除]", session.getTitle());
        assertEquals("manual", session.getTitleMode());
        assertNull(session.getProfileJson());
        assertNull(session.getResearchContextJson());
        verify(messages).purgeSessionContent(10L);
        verify(tools).purgeSessionContent(10L);
        verify(runs).purgeSessionContent(10L);
        verify(sessions).purgeSessionContent(eq(10L), eq("[已删除]"), eq("manual"), any(LocalDateTime.class));
    }

    @Test
    void historyPageUsesTheExtraRowOnlyToProduceAStableCursor() {
        AiAgentSession newest = activeSession();
        newest.setId(13L);
        newest.setLastMessageAt(LocalDateTime.of(2026, 7, 25, 12, 0));
        AiAgentSession second = activeSession();
        second.setId(12L);
        second.setLastMessageAt(LocalDateTime.of(2026, 7, 25, 11, 0));
        AiAgentSession extra = activeSession();
        extra.setId(11L);
        extra.setLastMessageAt(LocalDateTime.of(2026, 7, 25, 10, 0));
        when(sessions.selectHistory(eq(42L), eq("active"), eq(null), any(LocalDateTime.class),
                eq(null), eq(null), eq(null), eq(3)))
                .thenReturn(List.of(newest, second, extra));

        AgentSessionHistoryPageVO page = service.history(user, "active", "", null, 2);

        assertEquals(List.of(13L, 12L), page.items().stream().map(item -> item.sessionId()).toList());
        assertEquals(true, page.hasMore());
        assertEquals(true, page.nextCursor() != null && !page.nextCursor().isBlank());
    }

    @Test
    void historyCursorExpiresWhenTheOwnersMetadataRevisionChanges() {
        LocalDateTime snapshot = LocalDateTime.of(2026, 7, 26, 3, 0);
        AiAgentSession first = activeSession();
        first.setHistoryActivity(snapshot.minusMinutes(1));
        AiAgentSession extra = activeSession();
        extra.setId(9L);
        extra.setHistoryActivity(snapshot.minusMinutes(2));
        when(sessions.selectCurrentTimestamp()).thenReturn(snapshot);
        when(sessions.selectHistoryRevision(42L)).thenReturn(7L, 8L);
        when(sessions.selectHistory(eq(42L), eq("active"), eq(null), eq(snapshot),
                eq(null), eq(null), eq(null), eq(2))).thenReturn(List.of(first, extra));

        String cursor = service.history(user, "active", "", null, 1).nextCursor();
        AgentHistoryCursorStaleException exception = assertThrows(
                AgentHistoryCursorStaleException.class,
                () -> service.history(user, "active", "", cursor, 1));

        assertEquals("HISTORY_CURSOR_STALE", exception.getDiagnosticCode());
        verify(sessions, org.mockito.Mockito.times(2)).selectHistoryRevision(42L);
        verify(sessions, org.mockito.Mockito.times(1)).selectHistory(
                eq(42L), eq("active"), eq(null), eq(snapshot),
                eq(null), eq(null), eq(null), eq(2));
    }

    @Test
    void metadataMutationsAdvanceOnlyTheOwningUsersHistoryRevision() {
        AiAgentSession session = activeSession();
        when(sessions.selectOwnedForUpdate(10L, 42L)).thenReturn(session);
        when(sessions.updateById(any(AiAgentSession.class))).thenReturn(1);
        when(sessions.incrementHistoryRevision(42L)).thenReturn(1);
        AgentSessionUpdateDTO request = new AgentSessionUpdateDTO();
        request.setPinned(true);

        service.update(user, 10L, request);

        verify(sessions).incrementHistoryRevision(42L);
        verify(sessions, never()).incrementHistoryRevision(99L);
    }

    @Test
    void historySearchEscapesWildcardsBackslashAndTheExplicitEscapeCharacter() {
        when(sessions.selectHistory(eq(42L), eq("active"), eq("%100!%!_\\done!!%"), any(LocalDateTime.class),
                eq(null), eq(null), eq(null), eq(31)))
                .thenReturn(List.of());

        AgentSessionHistoryPageVO page = service.history(user, "active", "100%_\\done!", null, 30);

        assertEquals(List.of(), page.items());
        verify(sessions).selectHistory(eq(42L), eq("active"), eq("%100!%!_\\done!!%"), any(LocalDateTime.class),
                eq(null), eq(null), eq(null), eq(31));
    }

    @Test
    void historyRejectsUnknownScopesOversizedQueriesAndMalformedCursors() {
        assertThrows(BusinessException.class, () -> service.history(user, "other-user", "", null, 30));
        assertThrows(BusinessException.class, () -> service.history(user, "active", "研".repeat(101), null, 30));
        assertThrows(BusinessException.class, () -> service.history(user, "active", "", "not-a-cursor", 30));
        verify(sessions, never()).selectHistory(any(), any(), any(), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void messagePageIsAscendingAndProvidesTheOldestVisibleSequenceAsCursor() {
        AiAgentSession session = activeSession();
        when(sessions.selectOwned(10L, 42L)).thenReturn(session);
        AiAgentMessage fifth = message(5);
        AiAgentMessage fourth = message(4);
        AiAgentMessage third = message(3);
        when(messages.selectMessagePage(10L, null, 3)).thenReturn(List.of(fifth, fourth, third));

        AgentMessagePageVO page = service.messages(user, 10L, null, 2);

        assertEquals(List.of(4, 5), page.items().stream().map(item -> item.sequenceNo()).toList());
        assertEquals(4, page.nextBeforeSequence());
        assertEquals(true, page.hasMore());
    }

    @Test
    void scheduledCleanupPurgesOnlyDueUnlockedSessionsInABoundedBatch() {
        AiAgentSession due = activeSession();
        due.setDeletedAt(LocalDateTime.now().minusDays(31));
        due.setPurgeAfter(LocalDateTime.now().minusMinutes(1));
        when(sessions.selectDueForPurge(any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(List.of(due));
        when(runs.countNonTerminalForSession(10L)).thenReturn(0);
        when(sessions.purgeSessionContent(eq(10L), eq("[已删除]"), eq("manual"), any(LocalDateTime.class)))
                .thenReturn(1);

        assertEquals(1, service.purgeDue());
        assertEquals("manual", due.getTitleMode());
        verify(messages).purgeSessionContent(10L);
    }

    private AiAgentSession activeSession() {
        AiAgentSession session = new AiAgentSession();
        session.setId(10L);
        session.setUserId(42L);
        session.setTitle("新研究");
        session.setStatus("active");
        session.setTitleMode("auto");
        session.setVersion(0L);
        return session;
    }

    private AiAgentMessage message(int sequence) {
        AiAgentMessage message = new AiAgentMessage();
        message.setId((long) sequence);
        message.setSessionId(10L);
        message.setRole(sequence % 2 == 0 ? "assistant" : "user");
        message.setContent("message " + sequence);
        message.setStatus("completed");
        message.setSequenceNo(sequence);
        message.setCitationsJson("[]");
        return message;
    }
}
