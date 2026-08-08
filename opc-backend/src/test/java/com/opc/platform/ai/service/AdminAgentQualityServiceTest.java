package com.opc.platform.ai.service;

import com.opc.platform.ai.mapper.AdminAgentQualityMapper;
import com.opc.platform.ai.mapper.AdminAgentQualityFeedbackRow;
import com.opc.platform.ai.mapper.AdminAgentQualityRunRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAgentQualityServiceTest {

    @Test
    void aggregatesPersistedToolCallsAcrossRunGroups() {
        AdminAgentQualityMapper mapper = mock(AdminAgentQualityMapper.class);
        when(mapper.selectRunRows(any(), any(), any(), any(), any())).thenReturn(List.of(
                run("case_analysis", "deepseek-v4", "agent-research-v2", "completed", null,
                        8L, 1200L, 800L, 18L),
                run("case_analysis", "deepseek-v4", "agent-research-v2", "evidence_insufficient", "NO_POLICY",
                        2L, 150L, 200L, 1L),
                run("policy_lookup", "deepseek-v4", "agent-research-v2", "failed", "PROVIDER_TIMEOUT",
                        1L, 60L, 1200L, 3L)
        ));
        when(mapper.selectFeedbackRows(any(), any(), any(), any(), any())).thenReturn(List.of());

        var result = new AdminAgentQualityService(mapper)
                .quality(null, null, null, null, null, "day");

        assertEquals(22L, result.toolCallSummary().total());
        assertEquals(2L, result.toolCallSummary().average());
    }

    @Test
    void returnsOnlySanitizedFailureReasonCounts() {
        AdminAgentQualityMapper mapper = mock(AdminAgentQualityMapper.class);
        when(mapper.selectRunRows(any(), any(), any(), any(), any())).thenReturn(List.of(
                run("case_analysis", "deepseek-v4", "agent-research-v2", "failed", "PROVIDER_TIMEOUT",
                        2L, 0L, 0L),
                run("case_analysis", "deepseek-v4", "agent-research-v2", "expired", "AGENT_TIMEOUT",
                        1L, 0L, 0L),
                run("policy_lookup", "deepseek-v4", "agent-research-v2", "failed",
                        "private user question copied into diagnostics", 4L, 0L, 0L),
                run("policy_lookup", "deepseek-v4", "agent-research-v2", "completed", "IGNORED_COMPLETED_CODE",
                        5L, 0L, 0L),
                run("policy_lookup", "deepseek-v4", "agent-research-v2", "evidence_insufficient", "NO_POLICY",
                        3L, 0L, 0L),
                run("policy_lookup", "deepseek-v4", "agent-research-v2", "evidence_insufficient",
                        "private evidence detail", 2L, 0L, 0L)
        ));
        when(mapper.selectFeedbackRows(any(), any(), any(), any(), any())).thenReturn(List.of());

        var result = new AdminAgentQualityService(mapper)
                .quality(null, null, null, null, null, "day");

        assertEquals(2L, result.failureReasons().get("PROVIDER_TIMEOUT"));
        assertEquals(1L, result.failureReasons().get("AGENT_TIMEOUT"));
        assertEquals(4L, result.failureReasons().get("UNCLASSIFIED"));
        assertEquals(3, result.failureReasons().size());
        assertEquals(3L, result.evidenceInsufficientReasons().get("NO_POLICY"));
        assertEquals(2L, result.evidenceInsufficientReasons().get("UNCLASSIFIED"));
        assertFalse(result.toString().contains("private user question"));
        assertFalse(result.toString().contains("private evidence detail"));
    }

    @Test
    void returnsOnlyAggregatedQualityCostAndFailureSignals() {
        AdminAgentQualityMapper mapper = mock(AdminAgentQualityMapper.class);
        when(mapper.selectRunRows(any(), any(), any(), any(), any())).thenReturn(List.of(
                run("case_analysis", "deepseek-v4", "agent-research-v2", "completed", null, 8L, 1200L, 800L),
                run("case_analysis", "deepseek-v4", "agent-research-v2", "evidence_insufficient", "NO_POLICY", 2L, 150L, 200L),
                run("policy_lookup", "deepseek-v4", "agent-research-v2", "failed", "PROVIDER_TIMEOUT", 1L, 60L, 1200L)
        ));
        when(mapper.selectFeedbackRows(any(), any(), any(), any(), any())).thenReturn(List.of(
                feedback("helpful", "good_evidence", 5L),
                feedback("not_helpful", "missing_evidence", 2L)
        ));
        AdminAgentQualityService service = new AdminAgentQualityService(mapper);

        var result = service.quality(null, null, null, null, null, "day");

        assertEquals(11L, result.sampleSize());
        assertEquals(8L, result.completedCount());
        assertEquals(1L, result.failedCount());
        assertEquals(2L, result.evidenceInsufficientCount());
        assertEquals(5L, result.helpfulCount());
        assertEquals(2L, result.notHelpfulCount());
        assertEquals(5L, result.reasonCounts().get("good_evidence"));
        assertEquals(2L, result.evidenceInsufficientReasons().get("NO_POLICY"));
        assertFalse(result.toString().contains("comment"));
    }

    private AdminAgentQualityRunRow run(
            String taskType, String model, String promptVersion, String status, String diagnostic,
            Long count, Long tokens, Long latency
    ) {
        return run(taskType, model, promptVersion, status, diagnostic, count, tokens, latency, 0L);
    }

    private AdminAgentQualityRunRow run(
            String taskType, String model, String promptVersion, String status, String diagnostic,
            Long count, Long tokens, Long latency, Long toolCalls
    ) {
        AdminAgentQualityRunRow row = new AdminAgentQualityRunRow();
        row.setTaskType(taskType); row.setModel(model); row.setPromptVersion(promptVersion);
        row.setStatus(status); row.setDiagnosticCode(diagnostic); row.setRunCount(count);
        row.setTotalTokens(tokens); row.setLatencyMs(latency); row.setToolCallCount(toolCalls);
        return row;
    }

    private AdminAgentQualityFeedbackRow feedback(String rating, String reason, Long count) {
        AdminAgentQualityFeedbackRow row = new AdminAgentQualityFeedbackRow();
        row.setRating(rating); row.setReason(reason); row.setFeedbackCount(count);
        return row;
    }
}
