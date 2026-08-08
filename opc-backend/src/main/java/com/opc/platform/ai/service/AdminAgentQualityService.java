package com.opc.platform.ai.service;

import com.opc.platform.ai.mapper.AdminAgentQualityFeedbackRow;
import com.opc.platform.ai.mapper.AdminAgentQualityMapper;
import com.opc.platform.ai.mapper.AdminAgentQualityRunRow;
import com.opc.platform.ai.vo.AdminAgentQualityBreakdownVO;
import com.opc.platform.ai.vo.AdminAgentQualitySummaryVO;
import com.opc.platform.ai.vo.AdminAgentQualityVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AdminAgentQualityService {

    private static final Set<String> TASK_TYPES = Set.of(
            "auto", "case_analysis", "case_comparison", "technology_assessment",
            "policy_lookup", "source_verification", "general_research"
    );
    private static final Set<String> GRANULARITIES = Set.of("day", "week", "month");
    private static final Set<String> FAILURE_STATUSES = Set.of("failed", "expired");
    private static final Pattern CONTROLLED_DIAGNOSTIC_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,59}");
    private final AdminAgentQualityMapper mapper;

    public AdminAgentQualityVO quality(
            String dateFrom,
            String dateTo,
            String taskType,
            String model,
            String promptVersion,
            String granularity
    ) {
        LocalDateTime from = parseStart(dateFrom);
        LocalDateTime to = parseEnd(dateTo);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "质量统计时间范围无效");
        }
        String safeTaskType = controlledTaskType(taskType);
        String safeModel = bounded(model, 191, "模型筛选条件无效");
        String safePromptVersion = bounded(promptVersion, 60, "Prompt 版本筛选条件无效");
        String safeGranularity = granularity == null || granularity.isBlank() ? "day" : granularity.trim();
        if (!GRANULARITIES.contains(safeGranularity)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "质量统计粒度无效");
        }
        List<AdminAgentQualityRunRow> rows = safeRows(mapper.selectRunRows(
                from, to, safeTaskType, safeModel, safePromptVersion));
        List<AdminAgentQualityFeedbackRow> feedbackRows = safeFeedback(mapper.selectFeedbackRows(
                from, to, safeTaskType, safeModel, safePromptVersion));
        return aggregate(rows, feedbackRows, safeGranularity);
    }

    private AdminAgentQualityVO aggregate(
            List<AdminAgentQualityRunRow> rows,
            List<AdminAgentQualityFeedbackRow> feedbackRows,
            String granularity
    ) {
        long sampleSize = rows.stream().mapToLong(row -> count(row.getRunCount())).sum();
        long completed = countStatus(rows, "completed");
        long failed = countStatus(rows, "failed");
        long cancelled = countStatus(rows, "cancelled");
        long timeout = rows.stream().filter(row -> "expired".equals(row.getStatus())
                || "PROVIDER_TIMEOUT".equals(row.getDiagnosticCode()))
                .mapToLong(row -> count(row.getRunCount())).sum();
        long evidenceInsufficient = countStatus(rows, "evidence_insufficient");
        long latency = rows.stream().mapToLong(row -> count(row.getLatencyMs())).sum();
        long tokens = rows.stream().mapToLong(row -> count(row.getTotalTokens())).sum();
        long toolCalls = rows.stream().mapToLong(row -> count(row.getToolCallCount())).sum();
        Map<String, Long> reasonCounts = new LinkedHashMap<>();
        long helpful = 0;
        long notHelpful = 0;
        for (AdminAgentQualityFeedbackRow row : feedbackRows) {
            long value = count(row.getFeedbackCount());
            reasonCounts.merge(row.getReason(), value, Long::sum);
            if ("helpful".equals(row.getRating())) helpful += value;
            if ("not_helpful".equals(row.getRating())) notHelpful += value;
        }
        Map<String, Long> insufficientReasons = new LinkedHashMap<>();
        rows.stream().filter(row -> "evidence_insufficient".equals(row.getStatus()))
                .forEach(row -> insufficientReasons.merge(
                        controlledDiagnosticCode(row.getDiagnosticCode()), count(row.getRunCount()), Long::sum));
        Map<String, Long> failureReasons = new LinkedHashMap<>();
        rows.stream().filter(row -> FAILURE_STATUSES.contains(row.getStatus()))
                .forEach(row -> failureReasons.merge(
                        controlledDiagnosticCode(row.getDiagnosticCode()), count(row.getRunCount()), Long::sum));
        return new AdminAgentQualityVO(
                sampleSize, completed, failed, cancelled, timeout, evidenceInsufficient,
                helpful, notHelpful, helpful + notHelpful == 0 ? null : (double) helpful / (helpful + notHelpful),
                Map.copyOf(reasonCounts), Map.copyOf(insufficientReasons), Map.copyOf(failureReasons),
                breakdown(rows, true), breakdown(rows, false),
                new AdminAgentQualitySummaryVO(latency, sampleSize == 0 ? 0 : latency / sampleSize),
                new AdminAgentQualitySummaryVO(tokens, sampleSize == 0 ? 0 : tokens / sampleSize),
                new AdminAgentQualitySummaryVO(toolCalls, sampleSize == 0 ? 0 : toolCalls / sampleSize),
                granularity, LocalDateTime.now());
    }

    private List<AdminAgentQualityBreakdownVO> breakdown(List<AdminAgentQualityRunRow> rows, boolean task) {
        Map<String, long[]> values = new LinkedHashMap<>();
        for (AdminAgentQualityRunRow row : rows) {
            String key = task ? safeKey(row.getTaskType(), "auto") : safeKey(row.getModel(), "not_called");
            long[] metrics = values.computeIfAbsent(key, ignored -> new long[4]);
            long count = count(row.getRunCount());
            metrics[0] += count;
            if ("completed".equals(row.getStatus())) metrics[1] += count;
            if ("failed".equals(row.getStatus())) metrics[2] += count;
            if ("evidence_insufficient".equals(row.getStatus())) metrics[3] += count;
        }
        List<AdminAgentQualityBreakdownVO> result = new ArrayList<>();
        values.forEach((key, value) -> result.add(new AdminAgentQualityBreakdownVO(
                key, value[0], value[1], value[2], value[3])));
        return List.copyOf(result);
    }

    private long countStatus(List<AdminAgentQualityRunRow> rows, String status) {
        return rows.stream().filter(row -> status.equals(row.getStatus()))
                .mapToLong(row -> count(row.getRunCount())).sum();
    }

    private List<AdminAgentQualityRunRow> safeRows(List<AdminAgentQualityRunRow> rows) {
        return rows == null ? List.of() : rows;
    }

    private List<AdminAgentQualityFeedbackRow> safeFeedback(List<AdminAgentQualityFeedbackRow> rows) {
        return rows == null ? List.of() : rows;
    }

    private String controlledTaskType(String value) {
        String normalized = bounded(value, 40, "任务类型筛选条件无效");
        if (normalized != null && !TASK_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务类型筛选条件无效");
        }
        return normalized;
    }

    private String bounded(String value, int max, String message) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        return normalized;
    }

    private LocalDateTime parseStart(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime parseEnd(String value) {
        LocalDate date = parseDate(value);
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "质量统计日期格式无效");
        }
    }

    private long count(Long value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private String safeKey(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String controlledDiagnosticCode(String value) {
        if (value == null || value.isBlank()) return "UNCLASSIFIED";
        String normalized = value.trim();
        return CONTROLLED_DIAGNOSTIC_CODE.matcher(normalized).matches() ? normalized : "UNCLASSIFIED";
    }
}
