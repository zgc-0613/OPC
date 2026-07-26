package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAgentSessionMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentSessionService {

    public static final int MAX_USER_MESSAGE_LENGTH = 2000;
    public static final int MAX_ASSISTANT_MESSAGE_LENGTH = 12000;
    private static final Set<String> ROLES = Set.of("user", "assistant");
    private static final Set<String> MESSAGE_STATUSES = Set.of("pending", "completed", "failed");

    private final AiAgentSessionMapper sessionMapper;
    private final AiAgentMessageMapper messageMapper;
    private final AiAnalysisRunMapper runMapper;
    private final AgentSessionTitlePolicy titlePolicy;

    public AiAgentSession create(AuthenticatedUser user, String requestedTitle, String profileJson) {
        String title = StringUtils.hasText(requestedTitle) ? requestedTitle.trim() : "新研究";
        if (title.length() > 120) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "会话标题不能超过 120 个字符");
        }
        if (profileJson != null && profileJson.length() > 8000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "研究画像内容过长");
        }
        AiAgentSession session = new AiAgentSession();
        session.setUserId(user.userId());
        session.setTitle(title);
        session.setTitleMode(StringUtils.hasText(requestedTitle) ? "manual" : "auto");
        session.setStatus("active");
        session.setProfileJson(profileJson);
        session.setVersion(0L);
        sessionMapper.insert(session);
        session.setContentGeneration(0L);
        return session;
    }

    public List<AiAgentSession> list(AuthenticatedUser user) {
        List<AiAgentSession> sessions = sessionMapper.selectOwnedCompatibilityList(user.userId());
        return sessions == null ? List.of() : sessions;
    }

    public AiAgentSession requireOwned(AuthenticatedUser user, Long sessionId) {
        AiAgentSession session = sessionMapper.selectOwned(sessionId, user.userId());
        if (session == null || session.getPurgedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "研究会话不存在");
        }
        return session;
    }

    public void lockHistoryRevision(AuthenticatedUser user) {
        if (sessionMapper.lockHistoryRevision(user.userId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    public AiAgentSession lockOwned(AuthenticatedUser user, Long sessionId) {
        AiAgentSession session = sessionMapper.selectOwnedForUpdate(sessionId, user.userId());
        if (session == null || session.getPurgedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "研究会话不存在");
        }
        return session;
    }

    @Transactional
    public void updateResearchContext(AuthenticatedUser user, Long sessionId, String contextJson) {
        if (!StringUtils.hasText(contextJson) || contextJson.length() > 8000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "研究上下文格式无效");
        }
        AiAgentSession session = lockOwned(user, sessionId);
        if (!"active".equals(session.getStatus())
                || sessionMapper.updateResearchContext(sessionId, user.userId(), contextJson) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究会话状态已改变");
        }
    }

    @Transactional
    public void archive(AuthenticatedUser user, Long sessionId) {
        AiAgentSession session = lockOwned(user, sessionId);
        if ("archived".equals(session.getStatus())) return;
        if (runMapper.countNonTerminalForSession(sessionId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "运行中的研究会话不能归档");
        }
        session.setStatus("archived");
        session.setVersion(safeVersion(session.getVersion()) + 1);
        if (sessionMapper.updateById(session) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究会话已被其他操作修改");
        }
    }

    @Transactional
    public AiAgentMessage appendMessage(
            AuthenticatedUser user,
            Long sessionId,
            String role,
            String content,
            String status,
            Long runId,
            String citationsJson
    ) {
        AiAgentSession session = sessionMapper.selectOwnedForUpdate(sessionId, user.userId());
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "研究会话不存在");
        }
        if (!"active".equals(session.getStatus()) || session.getDeletedAt() != null
                || session.getPurgedAt() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "已归档会话不能继续发送消息");
        }
        validateMessage(role, content, status, citationsJson);
        AiAgentMessage message = new AiAgentMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content.trim());
        message.setStatus(status);
        message.setSequenceNo(messageMapper.maxSequence(sessionId) + 1);
        message.setRunId(runId);
        message.setCitationsJson(citationsJson);
        messageMapper.insert(message);
        if ("user".equals(role)) {
            int titleUpdated = sessionMapper.applyAutomaticTitle(
                    sessionId, user.userId(), titlePolicy.fromFirstQuestion(content));
            if (titleUpdated == 1 && sessionMapper.incrementHistoryRevision(user.userId()) != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "研究历史状态已变化");
            }
        }
        if (sessionMapper.touchActive(sessionId, user.userId()) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究会话状态已改变");
        }
        return message;
    }

    public List<AiAgentMessage> recentMessages(AuthenticatedUser user, Long sessionId, int requestedLimit) {
        requireOwned(user, sessionId);
        int limit = Math.max(1, Math.min(24, requestedLimit));
        List<AiAgentMessage> descending = messageMapper.selectRecent(sessionId, limit);
        if (descending == null || descending.isEmpty()) return List.of();
        List<AiAgentMessage> ascending = new ArrayList<>(descending);
        Collections.reverse(ascending);
        return List.copyOf(ascending);
    }

    private void validateMessage(String role, String content, String status, String citationsJson) {
        if (!ROLES.contains(role) || !MESSAGE_STATUSES.contains(status) || !StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息格式无效");
        }
        int maxLength = "user".equals(role) ? MAX_USER_MESSAGE_LENGTH : MAX_ASSISTANT_MESSAGE_LENGTH;
        if (content.trim().length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息内容过长");
        }
        if (citationsJson != null && citationsJson.length() > 12000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "引用内容过长");
        }
    }

    private long safeVersion(Long value) {
        return value == null ? 0L : value;
    }
}
