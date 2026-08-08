package com.opc.platform.ai.service;

import com.opc.platform.ai.dto.AgentRunFeedbackUpdateDTO;
import com.opc.platform.ai.entity.AgentRunFeedback;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AgentRunFeedbackMapper;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunFeedbackServiceTest {

    private final AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");

    @Test
    void ownerCanCreateFeedbackForCompletedRunWithControlledRatingAndReason() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentRunFeedbackMapper feedback = mock(AgentRunFeedbackMapper.class);
        AiAnalysisRun run = run(30L, "completed");
        when(runs.selectOwnedAgentRun(30L, 42L)).thenReturn(run);
        when(feedback.selectOwned(30L, 42L)).thenReturn(null);
        when(feedback.insert(any(AgentRunFeedback.class))).thenAnswer(invocation -> {
            AgentRunFeedback value = invocation.getArgument(0);
            value.setId(9L);
            return 1;
        });

        AgentRunFeedbackService service = new AgentRunFeedbackService(runs, messages, feedback);
        var result = service.upsert(owner, 30L, request("helpful", "good_evidence", "引用很清晰", 0L));

        assertEquals(30L, result.runId());
        assertEquals("helpful", result.rating());
        assertEquals("good_evidence", result.reason());
        assertEquals(1L, result.revision());
        verify(feedback).insert(any(AgentRunFeedback.class));
    }

    @Test
    void rejectsReasonOutsideTheRatingSpecificAllowlistBeforeWrite() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentRunFeedbackMapper feedback = mock(AgentRunFeedbackMapper.class);
        when(runs.selectOwnedAgentRun(30L, 42L)).thenReturn(run(30L, "completed"));
        AgentRunFeedbackService service = new AgentRunFeedbackService(runs, messages, feedback);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.upsert(owner, 30L, request("helpful", "missing_evidence", "", 0L)));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("FEEDBACK_REASON_RATING_MISMATCH", exception.getMessage());
        verify(feedback, never()).insert(any(AgentRunFeedback.class));
    }

    @Test
    void evidenceInsufficientNeedsAPersistedVisibleAssistantMessageBeforeFeedbackIsEligible() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentRunFeedbackMapper feedback = mock(AgentRunFeedbackMapper.class);
        when(runs.selectOwnedAgentRun(30L, 42L)).thenReturn(run(30L, "evidence_insufficient"));
        when(messages.selectFinalByRun(30L)).thenReturn(null);
        AgentRunFeedbackService service = new AgentRunFeedbackService(runs, messages, feedback);

        assertFalse(service.feedbackEligible(owner, 30L));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.upsert(owner, 30L, request("not_helpful", "missing_evidence", "", 0L)));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verify(feedback, never()).insert(any(AgentRunFeedback.class));
    }

    @Test
    void staleFeedbackRevisionCannotOverwriteAnotherResponse() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentRunFeedbackMapper feedback = mock(AgentRunFeedbackMapper.class);
        AgentRunFeedback existing = new AgentRunFeedback();
        existing.setId(9L); existing.setRunId(30L); existing.setUserId(42L);
        existing.setRating("helpful"); existing.setReason("good_evidence"); existing.setComment("清晰"); existing.setRevision(2L);
        when(runs.selectOwnedAgentRun(30L, 42L)).thenReturn(run(30L, "completed"));
        when(feedback.selectOwned(30L, 42L)).thenReturn(existing);
        AgentRunFeedbackService service = new AgentRunFeedbackService(runs, messages, feedback);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.upsert(owner, 30L, request("not_helpful", "unclear", "需要更清楚", 1L)));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("FEEDBACK_REVISION_CONFLICT", exception.getMessage());
        verify(feedback, never()).updateCas(eq(30L), eq(42L), any(), any(), any(), any(), any());
    }

    private AiAnalysisRun run(Long id, String status) {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(id); run.setUserId(42L); run.setStatus(status); run.setTaskType("agent_research");
        return run;
    }

    private AgentRunFeedbackUpdateDTO request(String rating, String reason, String comment, Long revision) {
        AgentRunFeedbackUpdateDTO request = new AgentRunFeedbackUpdateDTO();
        request.setRating(rating); request.setReason(reason); request.setComment(comment); request.setExpectedRevision(revision);
        return request;
    }
}
