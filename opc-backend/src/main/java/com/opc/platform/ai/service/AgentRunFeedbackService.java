package com.opc.platform.ai.service;

import com.opc.platform.ai.dto.AgentRunFeedbackUpdateDTO;
import com.opc.platform.ai.entity.AgentRunFeedback;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AgentRunFeedbackMapper;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.vo.AgentRunFeedbackVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgentRunFeedbackService {

    private static final Map<String, Set<String>> REASONS = Map.of(
            "helpful", Set.of("accurate_and_useful", "clear_and_actionable", "good_evidence", "other"),
            "not_helpful", Set.of("missing_evidence", "incorrect_claim", "not_relevant", "unclear", "too_slow", "other")
    );
    private static final Pattern HTML_TAG = Pattern.compile("<\\/?[a-zA-Z][^>]*>");

    private final AiAnalysisRunMapper runMapper;
    private final AiAgentMessageMapper messageMapper;
    private final AgentRunFeedbackMapper feedbackMapper;

    public AgentRunFeedbackVO get(AuthenticatedUser user, Long runId) {
        requireOwnedRun(user, runId);
        AgentRunFeedback feedback = feedbackMapper.selectOwned(runId, user.userId());
        return feedback == null ? null : view(feedback);
    }

    public boolean feedbackEligible(AuthenticatedUser user, Long runId) {
        AiAnalysisRun run = requireOwnedRun(user, runId);
        return feedbackEligible(run, messageMapper.selectFinalByRun(runId));
    }

    @Transactional
    public AgentRunFeedbackVO upsert(AuthenticatedUser user, Long runId, AgentRunFeedbackUpdateDTO request) {
        AiAnalysisRun run = requireOwnedRun(user, runId);
        if (!feedbackEligible(run, messageMapper.selectFinalByRun(runId))) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前研究结果暂不能评价");
        }
        ValidatedFeedback valid = validate(request);
        AgentRunFeedback existing = feedbackMapper.selectOwned(runId, user.userId());
        if (existing == null) {
            if (valid.expectedRevision() != 0L) throw revisionConflict();
            return create(user, runId, valid);
        }
        if (valid.expectedRevision() == 0L) {
            if (same(existing, valid)) return view(existing);
            throw revisionConflict();
        }
        if (!Objects.equals(existing.getRevision(), valid.expectedRevision())) throw revisionConflict();
        if (same(existing, valid)) return view(existing);
        LocalDateTime now = LocalDateTime.now();
        if (feedbackMapper.updateCas(runId, user.userId(), existing.getRevision(), valid.rating(),
                valid.reason(), valid.comment(), now) != 1) {
            throw revisionConflict();
        }
        existing.setRating(valid.rating());
        existing.setReason(valid.reason());
        existing.setComment(valid.comment());
        existing.setRevision(existing.getRevision() + 1);
        existing.setUpdatedAt(now);
        return view(existing);
    }

    private AgentRunFeedbackVO create(AuthenticatedUser user, Long runId, ValidatedFeedback valid) {
        LocalDateTime now = LocalDateTime.now();
        AgentRunFeedback feedback = new AgentRunFeedback();
        feedback.setUserId(user.userId());
        feedback.setRunId(runId);
        feedback.setRating(valid.rating());
        feedback.setReason(valid.reason());
        feedback.setComment(valid.comment());
        feedback.setRevision(1L);
        feedback.setCreatedAt(now);
        feedback.setUpdatedAt(now);
        try {
            feedbackMapper.insert(feedback);
            return view(feedback);
        } catch (DuplicateKeyException exception) {
            AgentRunFeedback raced = feedbackMapper.selectOwned(runId, user.userId());
            if (raced != null && same(raced, valid)) return view(raced);
            throw revisionConflict();
        }
    }

    private AiAnalysisRun requireOwnedRun(AuthenticatedUser user, Long runId) {
        if (user == null || user.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        AiAnalysisRun run = runMapper.selectOwnedAgentRun(runId, user.userId());
        if (run == null) throw new BusinessException(ErrorCode.NOT_FOUND, "研究运行不存在");
        return run;
    }

    boolean feedbackEligible(AiAnalysisRun run, AiAgentMessage finalMessage) {
        if ("completed".equals(run.getStatus())) return true;
        if (!"evidence_insufficient".equals(run.getStatus())) return false;
        return finalMessage != null && "assistant".equals(finalMessage.getRole())
                && "completed".equals(finalMessage.getStatus())
                && StringUtils.hasText(finalMessage.getContent());
    }

    private ValidatedFeedback validate(AgentRunFeedbackUpdateDTO request) {
        if (request == null || request.getExpectedRevision() == null || request.getExpectedRevision() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "FEEDBACK_EXPECTED_REVISION_INVALID");
        }
        String rating = trim(request.getRating());
        String reason = trim(request.getReason());
        if (!REASONS.containsKey(rating) || !REASONS.get(rating).contains(reason)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "FEEDBACK_REASON_RATING_MISMATCH");
        }
        return new ValidatedFeedback(rating, reason, sanitizeComment(request.getComment()), request.getExpectedRevision());
    }

    private String sanitizeComment(String value) {
        if (value == null) return null;
        String clean = HTML_TAG.matcher(value).replaceAll("").replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        if (clean.codePointCount(0, clean.length()) > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "反馈评论过长");
        }
        return clean.isEmpty() ? null : clean;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean same(AgentRunFeedback existing, ValidatedFeedback valid) {
        return Objects.equals(existing.getRating(), valid.rating())
                && Objects.equals(existing.getReason(), valid.reason())
                && Objects.equals(existing.getComment(), valid.comment());
    }

    private BusinessException revisionConflict() {
        return new BusinessException(ErrorCode.CONFLICT, "FEEDBACK_REVISION_CONFLICT");
    }

    private AgentRunFeedbackVO view(AgentRunFeedback feedback) {
        return new AgentRunFeedbackVO(feedback.getRunId(), feedback.getRating(), feedback.getReason(),
                feedback.getComment(), feedback.getRevision(), feedback.getUpdatedAt());
    }

    private record ValidatedFeedback(String rating, String reason, String comment, Long expectedRevision) {
    }
}
