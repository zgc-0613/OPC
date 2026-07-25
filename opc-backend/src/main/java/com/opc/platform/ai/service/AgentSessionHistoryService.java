package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.AgentSessionUpdateDTO;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAgentSessionMapper;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.ai.vo.AgentMessagePageVO;
import com.opc.platform.ai.vo.AgentMessageVO;
import com.opc.platform.ai.vo.AgentSessionHistoryPageVO;
import com.opc.platform.ai.vo.AgentSessionVO;
import com.opc.platform.ai.vo.AgentUsageVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentSessionHistoryService {

    private static final String PURGED_TITLE = "[已删除]";

    private final AiAgentSessionMapper sessionMapper;
    private final AiAgentMessageMapper messageMapper;
    private final AiAnalysisRunMapper runMapper;
    private final AiAgentToolCallMapper toolCallMapper;
    private final AiRuntimeSettingsProvider settingsProvider;
    private final ObjectMapper objectMapper;

    public AgentSessionHistoryPageVO history(
            AuthenticatedUser user, String requestedScope, String query, String encodedCursor, int requestedLimit
    ) {
        String scope = normalizeScope(requestedScope);
        int limit = Math.max(1, Math.min(50, requestedLimit));
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.codePointCount(0, trimmedQuery.length()) > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "历史搜索词不能超过 100 个字符");
        }
        String queryLike = trimmedQuery.isEmpty() ? null : "%" + escapeLike(trimmedQuery) + "%";
        HistoryCursor cursor = decodeCursor(encodedCursor);
        List<AiAgentSession> rows = sessionMapper.selectHistory(
                user.userId(), scope, queryLike,
                cursor == null ? null : cursor.pinned(),
                cursor == null ? null : cursor.activity(),
                cursor == null ? null : cursor.id(), limit + 1
        );
        List<AiAgentSession> safeRows = rows == null ? List.of() : rows;
        boolean hasMore = safeRows.size() > limit;
        List<AiAgentSession> visible = safeRows.subList(0, Math.min(limit, safeRows.size()));
        String nextCursor = hasMore && !visible.isEmpty()
                ? encodeCursor(visible.get(visible.size() - 1)) : null;
        return new AgentSessionHistoryPageVO(
                visible.stream().map(this::toSession).toList(), nextCursor, hasMore);
    }

    public AgentMessagePageVO messages(
            AuthenticatedUser user, Long sessionId, Integer beforeSequence, int requestedLimit
    ) {
        AiAgentSession session = sessionMapper.selectOwned(sessionId, user.userId());
        if (session == null || session.getPurgedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "研究会话不存在");
        }
        if (beforeSequence != null && beforeSequence < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息游标无效");
        }
        int limit = Math.max(1, Math.min(50, requestedLimit));
        List<AiAgentMessage> rows = messageMapper.selectMessagePage(sessionId, beforeSequence, limit + 1);
        List<AiAgentMessage> descending = rows == null ? List.of() : rows;
        boolean hasMore = descending.size() > limit;
        List<AiAgentMessage> visible = new ArrayList<>(descending.subList(0, Math.min(limit, descending.size())));
        Collections.reverse(visible);
        Integer nextBefore = hasMore && !visible.isEmpty() ? visible.get(0).getSequenceNo() : null;
        return new AgentMessagePageVO(visible.stream().map(this::toMessage).toList(), nextBefore, hasMore);
    }

    public AgentUsageVO usage(AuthenticatedUser user) {
        long used = Math.max(0L, value(runMapper.sumAgentTokensToday(user.userId())));
        long limit = Math.max(0L, settingsProvider.dailyTokenQuota());
        boolean unlimited = limit == 0;
        return new AgentUsageVO(
                used, limit, unlimited ? 0 : Math.max(0, limit - used), unlimited,
                LocalDate.now().plusDays(1).atStartOfDay()
        );
    }

    @Transactional
    public AiAgentSession archive(AuthenticatedUser user, Long sessionId) {
        AiAgentSession session = lockVisible(user, sessionId);
        requireNotDeleted(session);
        if ("archived".equals(session.getStatus())) return session;
        requireNoActiveRun(sessionId);
        session.setStatus("archived");
        session.setArchivedAt(LocalDateTime.now());
        session.setPinnedAt(null);
        persist(session);
        return session;
    }

    @Transactional
    public AiAgentSession unarchive(AuthenticatedUser user, Long sessionId) {
        AiAgentSession session = lockVisible(user, sessionId);
        requireNotDeleted(session);
        if ("active".equals(session.getStatus())) return session;
        session.setStatus("active");
        session.setArchivedAt(null);
        persist(session);
        return session;
    }

    @Transactional
    public AiAgentSession update(AuthenticatedUser user, Long sessionId, AgentSessionUpdateDTO request) {
        if (request == null || (!StringUtils.hasText(request.getTitle()) && request.getPinned() == null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要修改一个会话字段");
        }
        AiAgentSession session = lockVisible(user, sessionId);
        if (session.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "回收站中的会话不能修改");
        }
        if (request.getTitle() != null) {
            String title = request.getTitle().trim();
            if (title.isEmpty() || title.codePointCount(0, title.length()) > 80) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "会话标题不能为空且不能超过 80 个字符");
            }
            session.setTitle(title);
            session.setTitleMode("manual");
        }
        if (request.getPinned() != null) {
            session.setPinnedAt(Boolean.TRUE.equals(request.getPinned()) ? LocalDateTime.now() : null);
        }
        persist(session);
        return session;
    }

    @Transactional
    public AiAgentSession trash(AuthenticatedUser user, Long sessionId) {
        AiAgentSession session = lockVisible(user, sessionId);
        if (session.getDeletedAt() != null) return session;
        requireNoActiveRun(sessionId);
        LocalDateTime now = LocalDateTime.now();
        session.setDeletedAt(now);
        session.setPurgeAfter(now.plusDays(30));
        session.setPinnedAt(null);
        persist(session);
        return session;
    }

    @Transactional
    public AiAgentSession restore(AuthenticatedUser user, Long sessionId) {
        AiAgentSession session = lockVisible(user, sessionId);
        if (session.getDeletedAt() == null) return session;
        session.setDeletedAt(null);
        session.setPurgeAfter(null);
        persist(session);
        return session;
    }

    @Transactional
    public void purge(AuthenticatedUser user, Long sessionId) {
        AiAgentSession session = lockVisible(user, sessionId);
        if (session.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "会话必须先移入回收站");
        }
        requireNoActiveRun(sessionId);
        scrub(session, LocalDateTime.now());
    }

    @Transactional
    public int purgeDue() {
        LocalDateTime now = LocalDateTime.now();
        List<AiAgentSession> due = sessionMapper.selectDueForPurge(now, 20);
        if (due == null || due.isEmpty()) return 0;
        int purged = 0;
        for (AiAgentSession session : due) {
            if (runMapper.countNonTerminalForSession(session.getId()) > 0) continue;
            scrub(session, now);
            purged++;
        }
        return purged;
    }

    void scrub(AiAgentSession session, LocalDateTime now) {
        messageMapper.purgeSessionContent(session.getId());
        toolCallMapper.purgeSessionContent(session.getId());
        runMapper.purgeSessionContent(session.getId());
        if (sessionMapper.purgeSessionContent(session.getId(), PURGED_TITLE, "manual", now) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究会话状态已变化");
        }
        session.setTitle(PURGED_TITLE);
        session.setTitleMode("manual");
        session.setProfileJson(null);
        session.setResearchContextJson(null);
        session.setPinnedAt(null);
        session.setPurgedAt(now);
        session.setVersion((session.getVersion() == null ? 0L : session.getVersion()) + 1);
    }

    private AiAgentSession lockVisible(AuthenticatedUser user, Long sessionId) {
        AiAgentSession session = sessionMapper.selectOwnedForUpdate(sessionId, user.userId());
        if (session == null || session.getPurgedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "研究会话不存在");
        }
        return session;
    }

    private void requireNoActiveRun(Long sessionId) {
        if (runMapper.countNonTerminalForSession(sessionId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "运行中的研究会话不能删除");
        }
    }

    private void requireNotDeleted(AiAgentSession session) {
        if (session.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "回收站中的会话不能执行此操作");
        }
    }

    private void persist(AiAgentSession session) {
        session.setVersion((session.getVersion() == null ? 0L : session.getVersion()) + 1);
        if (sessionMapper.updateById(session) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究会话状态已变化");
        }
    }

    private AgentSessionVO toSession(AiAgentSession session) {
        return new AgentSessionVO(
                session.getId(), session.getTitle(), session.getTitleMode(), session.getStatus(),
                parseJson(session.getProfileJson(), true), session.getPinnedAt() != null,
                session.getArchivedAt(), session.getDeletedAt(), session.getPurgeAfter(),
                session.getActiveRunStatus(), session.getCreatedAt(), session.getUpdatedAt(),
                session.getLastMessageAt()
        );
    }

    public AgentSessionVO view(AiAgentSession session) {
        return toSession(session);
    }

    private AgentMessageVO toMessage(AiAgentMessage message) {
        return new AgentMessageVO(
                message.getId(), message.getRole(), message.getContent(), message.getStatus(),
                message.getSequenceNo(), message.getRunId(), parseJson(message.getCitationsJson(), false),
                message.getCreatedAt()
        );
    }

    private com.fasterxml.jackson.databind.JsonNode parseJson(String value, boolean objectFallback) {
        try {
            return value == null
                    ? (objectFallback ? objectMapper.createObjectNode() : objectMapper.createArrayNode())
                    : objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            return objectFallback ? objectMapper.createObjectNode() : objectMapper.createArrayNode();
        }
    }

    private String normalizeScope(String scope) {
        String normalized = scope == null || scope.isBlank() ? "active" : scope.trim().toLowerCase();
        if (!Set.of("active", "archived", "trash").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "历史范围无效");
        }
        return normalized;
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private String encodeCursor(AiAgentSession session) {
        LocalDateTime activity = session.getLastMessageAt() == null
                ? session.getCreatedAt() : session.getLastMessageAt();
        if (activity == null) activity = LocalDateTime.of(1970, 1, 1, 0, 0);
        String raw = (session.getPinnedAt() == null ? 0 : 1) + "|" + activity + "|" + session.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private HistoryCursor decodeCursor(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 3) throw new IllegalArgumentException();
            int pinned = Integer.parseInt(parts[0]);
            if (pinned != 0 && pinned != 1) throw new IllegalArgumentException();
            LocalDateTime activity = LocalDateTime.parse(parts[1]);
            long id = Long.parseLong(parts[2]);
            if (id < 1) throw new IllegalArgumentException();
            return new HistoryCursor(pinned, activity, id);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "历史游标无效");
        }
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private record HistoryCursor(int pinned, LocalDateTime activity, long id) {
    }
}
