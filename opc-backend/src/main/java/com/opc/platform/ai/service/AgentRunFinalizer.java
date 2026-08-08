package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AgentRunFinalizer {

    private final AiAnalysisRunMapper runMapper;
    private final AgentSessionService sessionService;
    private final AgentRunLifecycleService lifecycle;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiAgentMessage complete(
            AgentRunLease lease,
            AuthenticatedUser user,
            AgentOrchestratorOutcome outcome,
            String citationsJson
    ) {
        AiAgentSession session = sessionService.lockOwned(user, lease.run().getSessionId());
        AiAnalysisRun locked = runMapper.selectRunForUpdate(lease.run().getId());
        LocalDateTime now = LocalDateTime.now();
        boolean fenced = lease.executionAttempt() > 0 && lease.leaseOwner() != null;
        if (locked == null || !"running".equals(locked.getStatus())
                || !"active".equals(session.getStatus()) || session.getDeletedAt() != null
                || !Objects.equals(locked.getLeaseOwner(), lease.leaseOwner())
                || (fenced && !Objects.equals(locked.getExecutionAttempts(), lease.executionAttempt()))
                || value(locked.getSessionContentGeneration()) != value(session.getContentGeneration())
                || (locked.getLeaseExpiresAt() != null
                    && locked.getLeaseExpiresAt().isBefore(now))
                || (locked.getDeadlineAt() != null && !locked.getDeadlineAt().isAfter(now))) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究运行已取消、失败或过期，迟到结果已丢弃");
        }
        AiAgentMessage message = sessionService.appendMessage(
                user, locked.getSessionId(), "assistant", outcome.answer(), "completed", locked.getId(), citationsJson
        );
        var structuredResult = enrichWithAnalyticsDataVersion(locked, outcome.structuredResult());
        var analyticsSnapshot = analyticsSnapshotEnvelope(locked);
        lifecycle.complete(lease, outcome, safeResultJson(
                message.getId(), outcome.citations().size(), structuredResult, analyticsSnapshot,
                outcome.diagnosticCode()));
        return message;
    }

    private com.fasterxml.jackson.databind.JsonNode enrichWithAnalyticsDataVersion(
            AiAnalysisRun run,
            com.fasterxml.jackson.databind.JsonNode structuredResult
    ) {
        if (run.getAnalyticsSnapshotId() == null) return structuredResult;
        if (run.getAnalyticsMetricId() == null || run.getAnalyticsDataVersion() == null
                || run.getAnalyticsFiltersJson() == null || run.getAnalyticsSnapshotJson() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "ANALYTICS_SNAPSHOT_INVALID");
        }
        var result = structuredResult == null
                ? objectMapper.createObjectNode()
                : structuredResult.deepCopy();
        if (!result.isObject()) return structuredResult;
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) result;
        root.put("dataVersion", run.getAnalyticsDataVersion());
        return root;
    }

    private com.fasterxml.jackson.databind.JsonNode analyticsSnapshotEnvelope(AiAnalysisRun run) {
        if (run.getAnalyticsSnapshotId() == null) return null;
        var analytics = objectMapper.createObjectNode();
        analytics.put("analyticsSnapshotId", run.getAnalyticsSnapshotId());
        analytics.put("metricId", run.getAnalyticsMetricId());
        analytics.put("dataVersion", run.getAnalyticsDataVersion());
        try {
            analytics.set("filters", objectMapper.readTree(run.getAnalyticsFiltersJson()));
            analytics.set("snapshot", objectMapper.readTree(run.getAnalyticsSnapshotJson()));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "ANALYTICS_SNAPSHOT_INVALID");
        }
        return analytics;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private String safeResultJson(
            Long finalMessageId,
            int citationCount,
            com.fasterxml.jackson.databind.JsonNode structuredResult,
            com.fasterxml.jackson.databind.JsonNode analyticsSnapshot,
            String diagnosticCode
    ) {
        try {
            var root = objectMapper.createObjectNode();
            root.put("resultVersion", structuredResult == null ? "agent-research-v1" : "agent-research-v2");
            root.put("finalMessageId", finalMessageId);
            root.put("citationCount", citationCount);
            if (structuredResult != null && structuredResult.isObject()) {
                root.set("structuredResult", structuredResult);
            }
            if (diagnosticCode != null && !diagnosticCode.isBlank()) {
                root.put("diagnosticCode", diagnosticCode);
            }
            if (analyticsSnapshot != null && analyticsSnapshot.isObject()) {
                root.set("analyticsSnapshot", analyticsSnapshot);
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "研究结果审计摘要无法序列化");
        }
    }
}
