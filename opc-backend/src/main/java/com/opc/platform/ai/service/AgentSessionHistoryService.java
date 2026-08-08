package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.AgentSessionUpdateDTO;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.exception.AgentHistoryCursorStaleException;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAgentSessionMapper;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.mapper.AgentResearchReportMapper;
import com.opc.platform.ai.mapper.AgentUsageLedgerRow;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.ai.vo.AgentMessagePageVO;
import com.opc.platform.ai.vo.AgentMessageVO;
import com.opc.platform.ai.vo.AgentSessionHistoryPageVO;
import com.opc.platform.ai.vo.AgentSessionVO;
import com.opc.platform.ai.vo.AgentUsageVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class AgentSessionHistoryService {

    private static final String PURGED_TITLE = "[已删除]";

    private final AiAgentSessionMapper sessionMapper;
    private final AiAgentMessageMapper messageMapper;
    private final AiAnalysisRunMapper runMapper;
    private final AiAgentToolCallMapper toolCallMapper;
    private final AiRuntimeSettingsProvider settingsProvider;
    private final ObjectMapper objectMapper;
    private final String cursorSecret;
    private final AgentContentPurgeAuditService purgeAuditService;
    private final AgentResearchReportMapper reportMapper;

    public AgentSessionHistoryService(
            AiAgentSessionMapper sessionMapper,
            AiAgentMessageMapper messageMapper,
            AiAnalysisRunMapper runMapper,
            AiAgentToolCallMapper toolCallMapper,
            AiRuntimeSettingsProvider settingsProvider,
            ObjectMapper objectMapper,
            @Value("${opc.ai.agent.history-cursor-secret:${OPC_ASSISTANT_CURSOR_HMAC_SECRET:}}")
            String cursorSecret,
            AgentContentPurgeAuditService purgeAuditService,
            AgentResearchReportMapper reportMapper
    ) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.runMapper = runMapper;
        this.toolCallMapper = toolCallMapper;
        this.settingsProvider = settingsProvider;
        this.objectMapper = objectMapper;
        this.cursorSecret = cursorSecret;
        this.purgeAuditService = purgeAuditService;
        this.reportMapper = reportMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
        String queryHash = sha256(trimmedQuery);
        HistoryCursor cursor = decodeCursor(encodedCursor, user.userId(), scope, queryHash);
        long historyRevision = value(sessionMapper.selectHistoryRevision(user.userId()));
        if (cursor != null && cursor.revision() != historyRevision) {
            throw new AgentHistoryCursorStaleException();
        }
        LocalDateTime snapshotAt;
        if (cursor == null) {
            snapshotAt = sessionMapper.selectCurrentTimestamp();
            if (snapshotAt == null) snapshotAt = LocalDateTime.now();
        } else {
            snapshotAt = cursor.snapshotAt();
        }
        List<AiAgentSession> rows = sessionMapper.selectHistory(
                user.userId(), scope, queryLike, snapshotAt,
                cursor == null ? null : cursor.pinned(),
                cursor == null ? null : cursor.activity(),
                cursor == null ? null : cursor.id(), limit + 1
        );
        List<AiAgentSession> safeRows = rows == null ? List.of() : rows;
        boolean hasMore = safeRows.size() > limit;
        List<AiAgentSession> visible = safeRows.subList(0, Math.min(limit, safeRows.size()));
        String nextCursor = hasMore && !visible.isEmpty()
                ? encodeCursor(visible.get(visible.size() - 1), user.userId(), scope, queryHash,
                        snapshotAt, historyRevision) : null;
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
        AgentUsageLedgerRow ledger = runMapper.selectAgentUsageLedgerToday(user.userId());
        long used = Math.max(0L, ledger == null ? 0L : value(ledger.getUsedTokens()));
        long reserved = Math.max(0L, ledger == null ? 0L : value(ledger.getReservedTokens()));
        long limit = Math.max(0L, settingsProvider.dailyTokenQuota());
        boolean unlimited = limit == 0;
        return new AgentUsageVO(
                used, reserved, limit, limit,
                unlimited ? 0 : Math.max(0, limit - used - reserved), unlimited,
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
        try {
        AiAgentSession session = lockVisible(user, sessionId);
        if (session.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "会话必须先移入回收站");
        }
        requireSettledForPurge(sessionId);
        scrub(session, LocalDateTime.now());
        purgeAuditService.success("manual_purge", sessionId, user.userId(), "user", user.userId());
        } catch (BusinessException exception) {
            auditFailure("manual_purge", sessionId, user.userId(), "user", user.userId(),
                    "rejected", exception.getErrorCode().name());
            throw exception;
        } catch (RuntimeException exception) {
            auditFailure("manual_purge", sessionId, user.userId(), "user", user.userId(),
                    "failed", "PURGE_INTERNAL_ERROR");
            throw exception;
        }
    }

    @Transactional
    public int purgeDue() {
        LocalDateTime now = LocalDateTime.now();
        List<AiAgentSession> due = sessionMapper.selectDueForPurge(now, 20);
        if (due == null || due.isEmpty()) return 0;
        int purged = 0;
        for (AiAgentSession session : due) {
            if (hasActiveRunOrPendingSettlement(session.getId())) {
                auditFailure("scheduled_purge", session.getId(), session.getUserId(), "system", null,
                        "rejected", "ACTIVE_RUN");
                continue;
            }
            try {
                scrub(session, now);
                purgeAuditService.success(
                        "scheduled_purge", session.getId(), session.getUserId(), "system", null);
                purged++;
            } catch (RuntimeException exception) {
                auditFailure("scheduled_purge", session.getId(), session.getUserId(), "system", null,
                        "failed", "PURGE_INTERNAL_ERROR");
                throw exception;
            }
        }
        return purged;
    }

    void scrub(AiAgentSession session, LocalDateTime now) {
        messageMapper.purgeSessionContent(session.getId());
        toolCallMapper.purgeSessionContent(session.getId());
        runMapper.purgeSessionContent(session.getId());
        reportMapper.markSourceSessionUnavailable(session.getId(), session.getUserId(), now);
        if (sessionMapper.purgeSessionContent(session.getId(), PURGED_TITLE, "manual", now) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究会话状态已变化");
        }
        session.setTitle(PURGED_TITLE);
        session.setTitleMode("manual");
        session.setProfileJson(null);
        session.setResearchContextJson(null);
        session.setPinnedAt(null);
        session.setPurgedAt(now);
        session.setContentGeneration((session.getContentGeneration() == null ? 0L
                : session.getContentGeneration()) + 1L);
        session.setVersion((session.getVersion() == null ? 0L : session.getVersion()) + 1);
        incrementHistoryRevision(session.getUserId());
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

    private void requireSettledForPurge(Long sessionId) {
        if (hasActiveRunOrPendingSettlement(sessionId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究运行或 Token 结算尚未完成");
        }
    }

    private boolean hasActiveRunOrPendingSettlement(Long sessionId) {
        return runMapper.countNonTerminalForSession(sessionId) > 0
                || runMapper.countPendingProviderSettlementForSession(sessionId) > 0;
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
        incrementHistoryRevision(session.getUserId());
    }

    private AgentSessionVO toSession(AiAgentSession session) {
        JsonNode taskContext = parseOptionalJson(session.getTaskContextJson());
        return new AgentSessionVO(
                session.getId(), session.getTitle(), session.getTitleMode(), session.getStatus(),
                parseJson(session.getProfileJson(), true), session.getPinnedAt() != null,
                session.getArchivedAt(), session.getDeletedAt(), session.getPurgeAfter(),
                session.getActiveRunStatus(), session.getCreatedAt(), session.getUpdatedAt(),
                session.getLastMessageAt(), null, null, null, taskType(taskContext)
        );
    }

    public AgentSessionVO view(AiAgentSession session) {
        JsonNode taskContext = parseOptionalJson(session.getTaskContextJson());
        return new AgentSessionVO(
                session.getId(), session.getTitle(), session.getTitleMode(), session.getStatus(),
                parseJson(session.getProfileJson(), true), session.getPinnedAt() != null,
                session.getArchivedAt(), session.getDeletedAt(), session.getPurgeAfter(),
                session.getActiveRunStatus(), session.getCreatedAt(), session.getUpdatedAt(),
                session.getLastMessageAt(), taskContext, session.getTaskContextVersion(),
                session.getTaskContextHash(), taskType(taskContext)
        );
    }

    private String taskType(JsonNode taskContext) {
        return taskContext != null && taskContext.path("taskType").isTextual()
                ? taskContext.path("taskType").asText() : null;
    }

    private AgentMessageVO toMessage(AiAgentMessage message) {
        return new AgentMessageVO(
                message.getId(), message.getRole(), message.getContent(), message.getStatus(),
                message.getSequenceNo(), message.getRunId(), parseJson(message.getCitationsJson(), false),
                messageStructuredResult(message),
                messageAnalyticsSnapshot(message),
                message.getCreatedAt()
        );
    }

    private com.fasterxml.jackson.databind.JsonNode messageStructuredResult(AiAgentMessage message) {
        if (!"assistant".equals(message.getRole()) || !"completed".equals(message.getStatus())) return null;
        var result = parseOptionalJson(message.getStructuredResultJson());
        return result != null && result.isObject() ? result : null;
    }

    private com.fasterxml.jackson.databind.JsonNode messageAnalyticsSnapshot(AiAgentMessage message) {
        if (!"assistant".equals(message.getRole()) || !"completed".equals(message.getStatus())) return null;
        var snapshot = parseOptionalJson(message.getAnalyticsSnapshotJson());
        return snapshot != null && snapshot.isObject() ? snapshot : null;
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

    private com.fasterxml.jackson.databind.JsonNode parseOptionalJson(String value) {
        return value == null ? null : parseJson(value, true);
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

    private String encodeCursor(
            AiAgentSession session, Long userId, String scope, String queryHash,
            LocalDateTime snapshotAt, long historyRevision
    ) {
        LocalDateTime activity = session.getHistoryActivity() != null
                ? session.getHistoryActivity()
                : (session.getLastMessageAt() == null ? session.getCreatedAt() : session.getLastMessageAt());
        if (activity == null) activity = LocalDateTime.of(1970, 1, 1, 0, 0);
        int pinned = session.getHistoryPinned() == null
                ? (session.getPinnedAt() == null ? 0 : 1) : session.getHistoryPinned();
        String payload = "2|" + userId + "|" + scope + "|" + queryHash + "|" + snapshotAt
                + "|" + historyRevision + "|" + pinned + "|" + activity + "|" + session.getId();
        String raw = payload + "|" + hmac(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private HistoryCursor decodeCursor(String encoded, Long userId, String scope, String queryHash) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            boolean legacy = parts.length == 9 && "1".equals(parts[0]);
            boolean current = parts.length == 10 && "2".equals(parts[0]);
            if (!legacy && !current) throw new IllegalArgumentException();
            int signatureIndex = parts.length - 1;
            String payload = String.join("|", java.util.Arrays.copyOf(parts, signatureIndex));
            if (!MessageDigest.isEqual(
                    hmac(payload).getBytes(StandardCharsets.US_ASCII),
                    parts[signatureIndex].getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException();
            }
            if (!Long.toString(userId).equals(parts[1]) || !scope.equals(parts[2]) || !queryHash.equals(parts[3])) {
                throw new IllegalArgumentException();
            }
            LocalDateTime snapshotAt = LocalDateTime.parse(parts[4]);
            long revision = legacy ? -1L : Long.parseLong(parts[5]);
            int pinned = Integer.parseInt(parts[legacy ? 5 : 6]);
            if (pinned != 0 && pinned != 1) throw new IllegalArgumentException();
            LocalDateTime activity = LocalDateTime.parse(parts[legacy ? 6 : 7]);
            long id = Long.parseLong(parts[legacy ? 7 : 8]);
            if (id < 1) throw new IllegalArgumentException();
            return new HistoryCursor(snapshotAt, revision, pinned, activity, id);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "历史游标无效");
        }
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private void incrementHistoryRevision(Long userId) {
        if (sessionMapper.incrementHistoryRevision(userId) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "历史记录状态已变化，请重试");
        }
    }

    private void auditFailure(String operation, Long sessionId, Long userId, String operatorType,
                              Long operatorId, String result, String diagnosticCode) {
        try {
            purgeAuditService.failure(
                    operation, sessionId, userId, operatorType, operatorId, result, diagnosticCode);
        } catch (RuntimeException ignored) {
            // Audit storage failure must not replace the original purge outcome.
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String hmac(String value) {
        if (cursorSecret == null || cursorSecret.length() < 32) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Assistant history cursor signing is not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cursorSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }

    private record HistoryCursor(
            LocalDateTime snapshotAt, long revision, int pinned, LocalDateTime activity, long id
    ) {
    }
}
