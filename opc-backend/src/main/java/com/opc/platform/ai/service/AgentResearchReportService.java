package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.ai.dto.AgentResearchReportCreateDTO;
import com.opc.platform.ai.dto.AgentResearchReportRevisionDTO;
import com.opc.platform.ai.dto.AgentResearchReportUpdateDTO;
import com.opc.platform.ai.entity.AgentResearchReport;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AgentResearchReportMapper;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.vo.AgentResearchReportVO;
import com.opc.platform.ai.vo.AgentResearchReportPageVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.List;

@Service
public class AgentResearchReportService {

    private static final Pattern HTML_TAG = Pattern.compile("<\\/?[a-zA-Z][^>]*>");
    private static final String PDF_FONT_RESOURCE = "/fonts/ttf/NotoSansSC/NotoSansSC-Regular.ttf";
    private static final List<ExportSection> EXPORT_CLAIM_SECTIONS = List.of(
            new ExportSection("keyFindings", "关键发现"),
            new ExportSection("recommendations", "建议"),
            new ExportSection("risks", "风险与约束"),
            new ExportSection("assumptions", "关键假设"),
            new ExportSection("uncertainties", "不确定性")
    );
    private final AiAnalysisRunMapper runMapper;
    private final AiAgentMessageMapper messageMapper;
    private final AgentResearchReportMapper reportMapper;
    private final ObjectMapper objectMapper;
    private final SourceMapper sourceMapper;
    private final AgentRunEvidenceService evidenceService;

    @Autowired
    public AgentResearchReportService(
            AiAnalysisRunMapper runMapper,
            AiAgentMessageMapper messageMapper,
            AgentResearchReportMapper reportMapper,
            ObjectMapper objectMapper,
            SourceMapper sourceMapper,
            AgentRunEvidenceService evidenceService
    ) {
        this.runMapper = runMapper;
        this.messageMapper = messageMapper;
        this.reportMapper = reportMapper;
        this.objectMapper = objectMapper;
        this.sourceMapper = sourceMapper;
        this.evidenceService = evidenceService;
    }

    /** Test-only compatibility constructor for read and lifecycle behavior. */
    AgentResearchReportService(
            AiAnalysisRunMapper runMapper,
            AiAgentMessageMapper messageMapper,
            AgentResearchReportMapper reportMapper,
            ObjectMapper objectMapper,
            SourceMapper sourceMapper
    ) {
        this(runMapper, messageMapper, reportMapper, objectMapper, sourceMapper, null);
    }

    /** Test-only compatibility constructor. Production always supplies SourceMapper. */
    AgentResearchReportService(
            AiAnalysisRunMapper runMapper,
            AiAgentMessageMapper messageMapper,
            AgentResearchReportMapper reportMapper,
            ObjectMapper objectMapper
    ) {
        this(runMapper, messageMapper, reportMapper, objectMapper, null, null);
    }

    @Transactional
    public AgentResearchReportVO save(AuthenticatedUser user, Long sessionId, AgentResearchReportCreateDTO request) {
        String title = sanitizeRequired(request.getTitle(), 120);
        String notes = sanitizeOptional(request.getNotes(), 1000);
        AgentResearchReport existing = reportMapper.findByUserAndIdempotency(user.userId(), request.getIdempotencyKey());
        if (existing != null) {
            if (java.util.Objects.equals(existing.getSessionId(), sessionId)
                    && java.util.Objects.equals(existing.getFinalMessageId(), request.getFinalMessageId())
                    && java.util.Objects.equals(existing.getTitle(), title)
                    && java.util.Objects.equals(existing.getNotes(), notes)) {
                return toView(existing);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "REPORT_IDEMPOTENCY_CONFLICT");
        }
        AiAnalysisRun run = findRunForFinalMessage(user, request.getFinalMessageId());
        if (run == null || !"completed".equals(run.getStatus()) || !java.util.Objects.equals(run.getSessionId(), sessionId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能保存当前用户已完成的研究结果");
        }
        AiAgentMessage finalMessage = messageMapper.selectFinalByRun(run.getId());
        if (finalMessage == null || !java.util.Objects.equals(finalMessage.getId(), request.getFinalMessageId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究结果缺少可保存的最终消息");
        }
        JsonNode result = parse(run.getResultJson());
        JsonNode structured = result.path("structuredResult");
        String evidenceVersion = text(structured, "evidenceVersion");
        if (evidenceVersion == null || evidenceVersion.isBlank()) {
            throw new BusinessException(ErrorCode.CONFLICT, "研究结果缺少 evidenceVersion，无法保存报告");
        }
        JsonNode citations = parse(finalMessage.getCitationsJson());
        validateAuthorizedCitations(user, run.getId(), citations);
        AgentResearchReport report = new AgentResearchReport();
        report.setUserId(user.userId());
        report.setSessionId(sessionId);
        report.setRunId(run.getId());
        report.setFinalMessageId(finalMessage.getId());
        report.setIdempotencyKey(request.getIdempotencyKey());
        report.setTitle(title);
        report.setNotes(notes);
        report.setResultJson(write(withReportExportMetadata(result, run)));
        report.setCitationManifestJson(write(citationManifest(citations)));
        report.setEvidenceVersion(evidenceVersion);
        report.setDataVersion(text(structured, "dataVersion"));
        report.setSourceSessionAvailable(true);
        report.setStatus("active");
        report.setRevision(1L);
        reportMapper.insert(report);
        return toView(report);
    }

    public AgentResearchReportVO get(AuthenticatedUser user, Long reportId) {
        return toView(requireOwned(user, reportId));
    }

    @Transactional
    public AgentResearchReportVO update(
            AuthenticatedUser user,
            Long reportId,
            AgentResearchReportUpdateDTO request
    ) {
        AgentResearchReport report = requireOwned(user, reportId);
        if (!"active".equals(report.getStatus())) throw conflict();
        if (request == null || request.getExpectedRevision() == null
                || request.getExpectedRevision() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "REPORT_EXPECTED_REVISION_INVALID");
        }
        if (!java.util.Objects.equals(report.getRevision(), request.getExpectedRevision())) throw conflict();
        String title = sanitizeRequired(request.getTitle(), 120);
        String notes = sanitizeOptional(request.getNotes(), 1000);
        LocalDateTime now = LocalDateTime.now();
        if (reportMapper.updateMetadata(reportId, user.userId(), request.getExpectedRevision(), title, notes, now) != 1) {
            throw conflict();
        }
        report.setTitle(title);
        report.setNotes(notes);
        report.setRevision(report.getRevision() + 1);
        report.setUpdatedAt(now);
        return toView(report);
    }

    public List<AgentResearchReportVO> list(AuthenticatedUser user, String scope, String query, int limit) {
        String status = "trash".equalsIgnoreCase(scope) ? "trash" : "active";
        int bounded = Math.max(1, Math.min(100, limit));
        String safeQuery = query == null ? null : query.trim();
        if (safeQuery != null && safeQuery.length() > 80) throw new BusinessException(ErrorCode.BAD_REQUEST, "报告搜索条件过长");
        return reportMapper.listOwned(user.userId(), status, safeQuery, bounded).stream().map(this::toView).toList();
    }

    public AgentResearchReportPageVO page(
            AuthenticatedUser user, String scope, String query, String encodedCursor, int limit
    ) {
        String status = reportStatus(scope);
        int bounded = Math.max(1, Math.min(100, limit));
        String safeQuery = query == null ? null : query.trim();
        if (safeQuery != null && safeQuery.isBlank()) safeQuery = null;
        if (safeQuery != null && safeQuery.length() > 80) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "REPORT_QUERY_TOO_LONG");
        }
        ReportCursor cursor = decodeReportCursor(encodedCursor);
        LocalDateTime snapshotAt = cursor == null ? reportMapper.selectCurrentTimestamp() : cursor.snapshotAt();
        if (snapshotAt == null) snapshotAt = LocalDateTime.now();
        List<AgentResearchReport> rows = reportMapper.listOwnedPage(
                user.userId(), status, safeQuery, snapshotAt,
                cursor == null ? null : cursor.updatedAt(), cursor == null ? null : cursor.id(), bounded + 1);
        List<AgentResearchReport> safeRows = rows == null ? List.of() : rows;
        boolean hasMore = safeRows.size() > bounded;
        List<AgentResearchReport> visible = new ArrayList<>(
                safeRows.subList(0, Math.min(bounded, safeRows.size())));
        String nextCursor = hasMore && !visible.isEmpty()
                ? encodeReportCursor(snapshotAt, visible.get(visible.size() - 1)) : null;
        return new AgentResearchReportPageVO(
                visible.stream().map(this::toView).toList(), nextCursor, hasMore);
    }

    private String reportStatus(String scope) {
        if (scope == null || scope.isBlank() || "active".equalsIgnoreCase(scope)) return "active";
        if ("trash".equalsIgnoreCase(scope)) return "trash";
        throw new BusinessException(ErrorCode.BAD_REQUEST, "REPORT_SCOPE_INVALID");
    }

    private String encodeReportCursor(LocalDateTime snapshotAt, AgentResearchReport report) {
        String payload = snapshotAt + "|" + report.getUpdatedAt() + "|" + report.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private ReportCursor decodeReportCursor(String encoded) {
        if (!StringUtils.hasText(encoded)) return null;
        try {
            String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|", -1);
            if (fields.length != 3) throw new IllegalArgumentException();
            LocalDateTime snapshotAt = LocalDateTime.parse(fields[0]);
            LocalDateTime updatedAt = LocalDateTime.parse(fields[1]);
            long id = Long.parseLong(fields[2]);
            if (id <= 0 || updatedAt.isAfter(snapshotAt)) throw new IllegalArgumentException();
            return new ReportCursor(snapshotAt, updatedAt, id);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "REPORT_CURSOR_INVALID");
        }
    }

    private record ReportCursor(LocalDateTime snapshotAt, LocalDateTime updatedAt, long id) {}
    private record ExportSection(String field, String title) {}
    private record ExportMetadata(String label, String value) {}
    private record ReportExportSnapshot(
            AgentResearchReport report,
            JsonNode result,
            JsonNode structured,
            JsonNode safeStructured,
            JsonNode citations,
            String evidenceState
    ) {}

    public String exportMarkdown(AuthenticatedUser user, Long reportId) {
        return renderMarkdown(exportSnapshot(user, reportId));
    }

    private String renderMarkdown(ReportExportSnapshot snapshot) {
        AgentResearchReport report = snapshot.report();
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(markdownText(report.getTitle())).append("\n\n")
                .append("> 已保存的 AI 辅助研究结果快照。\n\n");
        appendMarkdownMetadata(markdown, snapshot);
        appendMarkdownTaskResult(markdown, snapshot.safeStructured().path("taskResult"));
        appendMarkdownTextSection(markdown, "备注", report.getNotes());
        appendMarkdownTextSection(markdown, "直接结论", summary(snapshot.structured()));
        for (ExportSection section : EXPORT_CLAIM_SECTIONS) {
            appendMarkdownClaims(markdown, section.title(), snapshot.structured().path(section.field()));
        }
        appendMarkdownQuestions(markdown, snapshot.structured().path("nextQuestions"));
        appendMarkdownSources(markdown, snapshot.citations());
        appendMarkdownEvidenceCoverage(markdown, snapshot.structured().path("evidenceCoverage"));
        appendMarkdownStructuredSnapshot(markdown, snapshot.safeStructured());
        return markdown.toString();
    }

    public String exportHtml(AuthenticatedUser user, Long reportId) {
        ReportExportSnapshot snapshot = exportSnapshot(user, reportId);
        AgentResearchReport report = snapshot.report();
        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\">")
                .append("<title>").append(escapeHtml(report.getTitle())).append("</title>")
                .append("<style>body{max-width:900px;margin:40px auto;padding:0 24px;color:#242824;background:#fff;font:16px/1.65 system-ui,sans-serif}")
                .append("h1,h2{font-family:Georgia,'Noto Serif SC',serif}h1{font-size:2rem}h2{margin-top:2rem;border-bottom:1px solid #d7dad5;padding-bottom:.35rem}")
                .append("dt{font-weight:700}dd{margin:0 0 .55rem}li{margin:.35rem 0}code,pre{font-family:ui-monospace,monospace}pre{overflow:auto;padding:16px;background:#f4f5f2;border:1px solid #d7dad5;white-space:pre-wrap;overflow-wrap:anywhere}")
                .append("a{color:#245a3d}@media print{body{margin:0;max-width:none}a{color:inherit}}</style></head><body>")
                .append("<main><h1>").append(escapeHtml(report.getTitle())).append("</h1>")
                .append("<p><strong>AI-assisted research result</strong> · 已保存的研究结果快照。</p>");
        appendHtmlMetadata(html, snapshot);
        appendHtmlTaskResult(html, snapshot.safeStructured().path("taskResult"));
        appendHtmlTextSection(html, "备注", report.getNotes());
        appendHtmlTextSection(html, "直接结论", summary(snapshot.structured()));
        for (ExportSection section : EXPORT_CLAIM_SECTIONS) {
            appendHtmlClaims(html, section.title(), snapshot.structured().path(section.field()));
        }
        appendHtmlQuestions(html, snapshot.structured().path("nextQuestions"));
        appendHtmlSources(html, snapshot.citations());
        appendHtmlEvidenceCoverage(html, snapshot.structured().path("evidenceCoverage"));
        appendHtmlStructuredSnapshot(html, snapshot.safeStructured());
        return html.append("</main></body></html>").toString();
    }

    public byte[] exportPdf(AuthenticatedUser user, Long reportId) {
        ReportExportSnapshot snapshot = exportSnapshot(user, reportId);
        try (PDDocument document = new PDDocument();
             InputStream fontStream = AgentResearchReportService.class.getResourceAsStream(PDF_FONT_RESOURCE)) {
            if (fontStream == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "REPORT_PDF_FONT_UNAVAILABLE");
            }
            // Keep the full CJK font program embedded so PDFBox can preserve a complete
            // ToUnicode map for saved Chinese report text. Subsetting this variable font
            // drops selected glyph mappings during extraction in PDFBox 3.x.
            PDType0Font font = PDType0Font.load(document, fontStream, false);
            try (PdfTextRenderer renderer = new PdfTextRenderer(document, font)) {
                for (String line : renderMarkdown(snapshot).split("\\R", -1)) {
                    renderer.writeMarkdownLine(line);
                }
                renderer.installToUnicodeMap();
            }
            document.getDocumentInformation().setTitle(snapshot.report().getTitle());
            document.getDocumentInformation().setSubject("Saved AI-assisted research result");
            document.getDocumentInformation().setCreator("SoloFirm");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "REPORT_PDF_GENERATION_FAILED");
        }
    }

    @Transactional
    public AgentResearchReportVO trash(AuthenticatedUser user, Long reportId, AgentResearchReportRevisionDTO request) {
        AgentResearchReport report = requireOwned(user, reportId);
        checkRevision(report, request);
        if ("trash".equals(report.getStatus())) return toView(report);
        if (!"active".equals(report.getStatus())) throw conflict();
        LocalDateTime now = LocalDateTime.now();
        if (reportMapper.trash(reportId, user.userId(), request.getExpectedRevision(), now, now.plusDays(30)) != 1) throw conflict();
        return get(user, reportId);
    }

    @Transactional
    public AgentResearchReportVO restore(AuthenticatedUser user, Long reportId, AgentResearchReportRevisionDTO request) {
        AgentResearchReport report = requireOwned(user, reportId);
        checkRevision(report, request);
        if ("active".equals(report.getStatus())) return toView(report);
        if (!"trash".equals(report.getStatus())) throw conflict();
        LocalDateTime now = LocalDateTime.now();
        if (reportMapper.restore(reportId, user.userId(), request.getExpectedRevision(), now) != 1) throw conflict();
        return get(user, reportId);
    }

    @Transactional
    public AgentResearchReportVO permanent(AuthenticatedUser user, Long reportId, AgentResearchReportRevisionDTO request) {
        AgentResearchReport report = requireOwned(user, reportId);
        checkRevision(report, request);
        if (!"trash".equals(report.getStatus())) throw conflict();
        LocalDateTime now = LocalDateTime.now();
        if (reportMapper.permanentlyPurge(reportId, user.userId(), request.getExpectedRevision(), now) != 1) throw conflict();
        return get(user, reportId);
    }

    @Transactional
    public int purgeDue() {
        LocalDateTime now = LocalDateTime.now();
        List<AgentResearchReport> reports = reportMapper.selectDueForPurge(now, 20);
        if (reports == null || reports.isEmpty()) return 0;
        int purged = 0;
        for (AgentResearchReport report : reports) {
            if (!"trash".equals(report.getStatus()) || report.getRevision() == null) continue;
            if (reportMapper.permanentlyPurgeDue(report.getId(), report.getRevision(), now) != 1) continue;
            report.setStatus("permanently_purged");
            report.setRevision(report.getRevision() + 1);
            report.setTitle(null);
            report.setNotes(null);
            report.setResultJson(null);
            report.setCitationManifestJson(null);
            report.setEvidenceVersion(null);
            report.setDataVersion(null);
            report.setPurgedAt(now);
            report.setUpdatedAt(now);
            purged++;
        }
        return purged;
    }

    private AiAnalysisRun findRunForFinalMessage(AuthenticatedUser user, Long messageId) {
        // The message id is already owner-bound by the final-message query. The mapper lookup below is added by the report migration.
        return runMapper.selectOwnedAgentRunByFinalMessage(messageId, user.userId());
    }

    private AgentResearchReport requireOwned(AuthenticatedUser user, Long reportId) {
        AgentResearchReport report = reportMapper.selectOwned(reportId, user.userId());
        if (report == null) throw new BusinessException(ErrorCode.NOT_FOUND, "报告不存在");
        return report;
    }

    private void checkRevision(AgentResearchReport report, AgentResearchReportRevisionDTO request) {
        if (request == null || request.getExpectedRevision() == null || request.getExpectedRevision() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "REPORT_EXPECTED_REVISION_INVALID");
        }
        if (!java.util.Objects.equals(report.getRevision(), request.getExpectedRevision())) throw conflict();
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.CONFLICT, "REPORT_REVISION_CONFLICT");
    }

    private JsonNode withReportExportMetadata(JsonNode result, AiAnalysisRun run) {
        if (!(result instanceof ObjectNode objectResult)) return result;
        ObjectNode snapshot = objectResult.deepCopy();
        ObjectNode metadata = snapshot.putObject("reportExportMetadata");
        putIfText(metadata, "provider", run.getProvider());
        putIfText(metadata, "modelId", run.getModelId());
        putIfText(metadata, "promptVersion", run.getPromptVersion());
        putIfText(metadata, "requestedIntent", run.getRequestedIntent());
        putIfText(metadata, "taskType", run.getTaskType());
        putIfText(metadata, "analyticsMetricId", run.getAnalyticsMetricId());
        if (run.getCompletedAt() != null) metadata.put("runCompletedAt", run.getCompletedAt().toString());
        return snapshot;
    }

    private void putIfText(ObjectNode target, String field, String value) {
        if (StringUtils.hasText(value)) target.put(field, value.trim());
    }

    private String sanitizeRequired(String value, int max) {
        String clean = sanitizeOptional(value, max);
        if (clean == null || clean.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "报告标题不能为空");
        return clean;
    }

    private String sanitizeOptional(String value, int max) {
        if (value == null) return null;
        String clean = HTML_TAG.matcher(value).replaceAll("").trim();
        if (clean.length() > max) throw new BusinessException(ErrorCode.BAD_REQUEST, "报告文本过长");
        return clean;
    }

    private JsonNode parse(String value) {
        try { return value == null ? objectMapper.createObjectNode() : objectMapper.readTree(value); }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.CONFLICT, "研究结果格式无效，无法保存报告"); }
    }

    private String write(JsonNode value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "报告结果无法保存"); }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private AgentResearchReport requireExportableReport(AuthenticatedUser user, Long reportId) {
        AgentResearchReport report = requireOwned(user, reportId);
        if (!"active".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "REPORT_EXPORT_REQUIRES_ACTIVE");
        }
        return report;
    }

    private ReportExportSnapshot exportSnapshot(AuthenticatedUser user, Long reportId) {
        AgentResearchReport report = requireExportableReport(user, reportId);
        JsonNode result = parse(report.getResultJson());
        JsonNode structured = result.path("structuredResult");
        if (!structured.isObject()) structured = result.isObject() ? result : objectMapper.createObjectNode();
        JsonNode citations = parse(report.getCitationManifestJson());
        return new ReportExportSnapshot(
                report, result, structured, sanitizeStructuredSnapshot(structured), citations, evidenceState(report));
    }

    private String summary(JsonNode structured) {
        String value = text(structured, "directAnswer");
        return StringUtils.hasText(value) ? value : text(structured, "summary");
    }

    private List<ExportMetadata> exportMetadata(ReportExportSnapshot snapshot) {
        JsonNode metadata = snapshot.result().path("reportExportMetadata");
        JsonNode structured = snapshot.structured();
        AgentResearchReport report = snapshot.report();
        String generatedAt = firstText(
                text(structured, "generatedAt"), text(metadata, "runCompletedAt"), stringValue(report.getCreatedAt()));
        String taskType = firstText(
                text(structured, "taskType"), text(metadata, "requestedIntent"), text(metadata, "taskType"));
        String resultVersion = firstText(text(structured, "schemaVersion"), text(snapshot.result(), "resultVersion"));
        return List.of(
                new ExportMetadata("报告生成时间", stringValue(report.getCreatedAt())),
                new ExportMetadata("结果生成时间", generatedAt),
                new ExportMetadata("模型提供方", recordedValue(text(metadata, "provider"))),
                new ExportMetadata("模型", recordedValue(text(metadata, "modelId"))),
                new ExportMetadata("提示词版本", recordedValue(text(metadata, "promptVersion"))),
                new ExportMetadata("任务类型", recordedValue(taskType)),
                new ExportMetadata("结果结构版本", recordedValue(resultVersion)),
                new ExportMetadata("证据版本", recordedValue(report.getEvidenceVersion())),
                new ExportMetadata("数据版本", StringUtils.hasText(report.getDataVersion()) ? report.getDataVersion() : "不适用"),
                new ExportMetadata("置信度", recordedValue(text(structured, "confidence"))),
                new ExportMetadata("当前证据状态", recordedValue(snapshot.evidenceState()))
        );
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return null;
    }

    private String recordedValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "未记录（旧报告快照）";
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void appendMarkdownMetadata(StringBuilder markdown, ReportExportSnapshot snapshot) {
        markdown.append("## 报告信息\n\n");
        for (ExportMetadata field : exportMetadata(snapshot)) {
            markdown.append("- **").append(field.label()).append("：** ")
                    .append(markdownText(recordedValue(field.value()))).append("\n");
        }
        markdown.append("\n");
    }

    private void appendMarkdownTextSection(StringBuilder markdown, String title, String value) {
        if (!StringUtils.hasText(value)) return;
        markdown.append("## ").append(title).append("\n\n")
                .append(markdownText(value)).append("\n\n");
    }

    private void appendMarkdownTaskResult(StringBuilder markdown, JsonNode task) {
        if (!task.isObject() || !StringUtils.hasText(text(task, "type"))) return;
        String type = text(task, "type");
        markdown.append("## Task-specific result\n\n");
        appendMarkdownProperty(markdown, "taskType", type);
        switch (type) {
            case "case_analysis" -> {
                appendMarkdownProperty(markdown, "caseId", scalarValues(task.path("caseId")));
                appendMarkdownJsonSection(markdown, "sections", task.path("sections"));
            }
            case "case_comparison" -> {
                appendMarkdownProperty(markdown, "caseIds", scalarValues(task.path("caseIds")));
                appendMarkdownProperty(markdown, "dimensions", scalarValues(task.path("dimensions")));
                appendMarkdownJsonSection(markdown, "baselines", task.path("baselines"));
                appendMarkdownJsonSection(markdown, "comparisons", task.path("comparisons"));
            }
            case "technology_assessment" -> {
                JsonNode context = task.path("assessmentContext");
                appendMarkdownProperty(markdown, "technologyTagId", scalarValues(context.path("technologyTagId")));
                markdown.append("### Technology assessment\n\n");
                markdown.append("### Assessment context\n\n");
                for (String field : List.of("technologyText", "applicationScenario", "teamCapabilities",
                        "timeline", "existingResources", "constraints")) {
                    appendMarkdownProperty(markdown, field, text(context, field));
                }
                appendMarkdownJsonSection(markdown, "dimensions", task.path("dimensions"));
                appendMarkdownProperty(markdown, "supportingCases", scalarValues(task.path("supportingCases")));
                appendMarkdownProperty(markdown, "relatedPolicies", scalarValues(task.path("relatedPolicies")));
            }
            case "policy_lookup" -> {
                appendMarkdownProperty(markdown, "policyIds", scalarValues(task.path("policyIds")));
                appendMarkdownJsonSection(markdown, "policySections", task);
            }
            case "source_verification" -> {
                appendMarkdownProperty(markdown, "mode", text(task, "mode"));
                appendMarkdownProperty(markdown, "sourceId", scalarValues(task.path("sourceId")));
                appendMarkdownProperty(markdown, "verdict", text(task, "verdict"));
                appendMarkdownProperty(markdown, "verdictExplanation", text(task, "verdictExplanation"));
                appendMarkdownJsonSection(markdown, "supportedClaims", task.path("supportedClaims"));
                appendMarkdownJsonSection(markdown, "unsupportedClaims", task.path("unsupportedClaims"));
                appendMarkdownJsonSection(markdown, "conflicts", task.path("conflicts"));
            }
            case "general_research" -> appendMarkdownJsonSection(markdown, "sections", task.path("sections"));
            default -> appendMarkdownJsonSection(markdown, "savedTaskResult", task);
        }
        markdown.append("\n");
    }

    private void appendMarkdownJsonSection(StringBuilder markdown, String title, JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return;
        markdown.append("### ").append(title).append("\n\n");
        for (String line : prettyJson(value).split("\\R", -1)) {
            markdown.append("    ").append(escapeHtml(line)).append("\n");
        }
        markdown.append("\n");
    }

    private void appendMarkdownClaims(StringBuilder markdown, String title, JsonNode claims) {
        if (!claims.isArray() || claims.isEmpty()) return;
        markdown.append("## ").append(title).append("\n\n");
        int index = 0;
        for (JsonNode claim : claims) {
            markdown.append(++index).append(". ")
                    .append(markdownText(claim.path("text").asText(""))).append("\n");
            appendMarkdownProperty(markdown, "结论 ID", text(claim, "id"));
            appendMarkdownProperty(markdown, "类型", text(claim, "kind"));
            appendMarkdownProperty(markdown, "来源 ID", scalarValues(claim.path("sourceIds")));
            appendMarkdownProperty(markdown, "置信度", text(claim, "confidence"));
            if (claim.path("missingEvidence").isBoolean()) {
                appendMarkdownProperty(markdown, "缺失证据", claim.path("missingEvidence").asBoolean() ? "是" : "否");
            }
        }
        markdown.append("\n");
    }

    private void appendMarkdownQuestions(StringBuilder markdown, JsonNode questions) {
        if (!questions.isArray() || questions.isEmpty()) return;
        markdown.append("## 后续研究问题\n\n");
        for (JsonNode question : questions) {
            if (question.isTextual()) markdown.append("- ").append(markdownText(question.asText())).append("\n");
        }
        markdown.append("\n");
    }

    private void appendMarkdownProperty(StringBuilder markdown, String label, String value) {
        if (!StringUtils.hasText(value)) return;
        markdown.append("   - ").append(label).append("：").append(markdownText(value)).append("\n");
    }

    private void appendMarkdownSources(StringBuilder markdown, JsonNode citations) {
        markdown.append("## Sources / 保存的引用\n\n");
        if (!citations.isArray() || citations.isEmpty()) {
            markdown.append("此报告没有保存的引用来源。\n\n");
            return;
        }
        int index = 0;
        for (JsonNode citation : citations) {
            String title = citation.path("title").asText("来源 #" + citation.path("sourceId").asLong());
            String url = safeUrl(citation.path("url").asText(""));
            markdown.append(++index).append(". ").append(markdownText(title)).append("\n");
            appendMarkdownProperty(markdown, "来源 ID", scalarValues(citation.path("sourceId")));
            appendMarkdownProperty(markdown, "发布方", text(citation, "publisher"));
            appendMarkdownProperty(markdown, "保存时主张", text(citation, "claim"));
            appendMarkdownProperty(markdown, "内容类型", text(citation, "contentType"));
            appendMarkdownProperty(markdown, "保存时核验状态", text(citation, "verificationStatus"));
            appendMarkdownProperty(markdown, "证据修订", scalarValues(citation.path("evidenceRevision")));
            appendMarkdownProperty(markdown, "保存时可用性", text(citation, "availability"));
            if (!url.isBlank()) markdown.append("   - 链接：<").append(url).append(">\n");
        }
        markdown.append("\n");
    }

    private void appendMarkdownEvidenceCoverage(StringBuilder markdown, JsonNode coverage) {
        markdown.append("## 证据与限制\n\n")
                .append("引用来自保存时授权的研究证据；来源或证据发生变化时，应重新核验后再据此决策。\n\n");
        for (String field : List.of(
                "status", "factClaimCount", "citedFactClaimCount", "missingEvidenceFactCount", "ratio")) {
            appendMarkdownProperty(markdown, field, scalarValues(coverage.path(field)));
        }
        JsonNode limitations = coverage.path("limitations");
        if (limitations.isArray() && !limitations.isEmpty()) {
            markdown.append("\n### 已保存的限制\n\n");
            for (JsonNode limitation : limitations) {
                if (limitation.isTextual()) markdown.append("- ").append(markdownText(limitation.asText())).append("\n");
            }
            markdown.append("\n");
        }
    }

    private void appendMarkdownStructuredSnapshot(StringBuilder markdown, JsonNode structured) {
        markdown.append("## 完整结构化结果快照\n\n");
        for (String line : prettyJson(structured).split("\\R", -1)) {
            markdown.append("    ").append(escapeHtml(line)).append("\n");
        }
        markdown.append("\n");
    }

    private void appendHtmlMetadata(StringBuilder html, ReportExportSnapshot snapshot) {
        html.append("<section><h2>报告信息</h2><dl>");
        for (ExportMetadata field : exportMetadata(snapshot)) {
            appendHtmlDefinition(html, field.label(), recordedValue(field.value()));
        }
        html.append("</dl></section>");
    }

    private void appendHtmlTextSection(StringBuilder html, String title, String value) {
        if (!StringUtils.hasText(value)) return;
        html.append("<section><h2>").append(escapeHtml(title)).append("</h2><p>")
                .append(htmlText(value)).append("</p></section>");
    }

    private void appendHtmlTaskResult(StringBuilder html, JsonNode task) {
        if (!task.isObject() || !StringUtils.hasText(text(task, "type"))) return;
        String type = text(task, "type");
        html.append("<section><h2>Task-specific result</h2><dl>");
        appendHtmlDefinition(html, "taskType", type);
        if ("technology_assessment".equals(type)) {
            JsonNode context = task.path("assessmentContext");
            appendHtmlDefinition(html, "technologyTagId", scalarValues(context.path("technologyTagId")));
            html.append("</dl><h3>Technology assessment</h3><h3>Assessment context</h3><dl>");
            for (String field : List.of("technologyText", "applicationScenario", "teamCapabilities",
                    "timeline", "existingResources", "constraints")) {
                appendHtmlDefinition(html, field, text(context, field));
            }
        } else if ("source_verification".equals(type)) {
            appendHtmlDefinition(html, "mode", text(task, "mode"));
            appendHtmlDefinition(html, "sourceId", scalarValues(task.path("sourceId")));
            appendHtmlDefinition(html, "verdict", text(task, "verdict"));
            appendHtmlDefinition(html, "verdictExplanation", text(task, "verdictExplanation"));
        }
        html.append("</dl>");
        String jsonKey = "technology_assessment".equals(type) ? "dimensions"
                : "case_analysis".equals(type) ? "sections"
                : "case_comparison".equals(type) ? "comparisons"
                : "general_research".equals(type) ? "sections" : null;
        if (jsonKey != null) appendHtmlJsonSection(html, jsonKey, task.path(jsonKey));
        html.append("</section>");
    }

    private void appendHtmlJsonSection(StringBuilder html, String title, JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return;
        html.append("<h3>").append(escapeHtml(title)).append("</h3><pre><code>")
                .append(escapeHtml(prettyJson(value))).append("</code></pre>");
    }

    private void appendHtmlClaims(StringBuilder html, String title, JsonNode claims) {
        if (!claims.isArray() || claims.isEmpty()) return;
        html.append("<section><h2>").append(escapeHtml(title)).append("</h2><ol>");
        for (JsonNode claim : claims) {
            html.append("<li><p>").append(htmlText(claim.path("text").asText(""))).append("</p><dl>");
            appendHtmlDefinition(html, "结论 ID", text(claim, "id"));
            appendHtmlDefinition(html, "类型", text(claim, "kind"));
            appendHtmlDefinition(html, "来源 ID", scalarValues(claim.path("sourceIds")));
            appendHtmlDefinition(html, "置信度", text(claim, "confidence"));
            if (claim.path("missingEvidence").isBoolean()) {
                appendHtmlDefinition(html, "缺失证据", claim.path("missingEvidence").asBoolean() ? "是" : "否");
            }
            html.append("</dl></li>");
        }
        html.append("</ol></section>");
    }

    private void appendHtmlQuestions(StringBuilder html, JsonNode questions) {
        if (!questions.isArray() || questions.isEmpty()) return;
        html.append("<section><h2>后续研究问题</h2><ul>");
        for (JsonNode question : questions) {
            if (question.isTextual()) html.append("<li>").append(htmlText(question.asText())).append("</li>");
        }
        html.append("</ul></section>");
    }

    private void appendHtmlSources(StringBuilder html, JsonNode citations) {
        html.append("<section><h2>保存的引用</h2>");
        if (!citations.isArray() || citations.isEmpty()) {
            html.append("<p>此报告没有保存的引用来源。</p></section>");
            return;
        }
        html.append("<ol>");
        for (JsonNode citation : citations) {
            String title = citation.path("title").asText("来源 #" + citation.path("sourceId").asLong());
            String url = safeUrl(citation.path("url").asText(""));
            html.append("<li><p><strong>");
            if (url.isBlank()) {
                html.append(escapeHtml(title));
            } else {
                html.append("<a target=\"_blank\" rel=\"noopener noreferrer\" href=\"")
                        .append(escapeHtml(url)).append("\">").append(escapeHtml(title)).append("</a>");
            }
            html.append("</strong></p><dl>");
            appendHtmlDefinition(html, "来源 ID", scalarValues(citation.path("sourceId")));
            appendHtmlDefinition(html, "发布方", text(citation, "publisher"));
            appendHtmlDefinition(html, "保存时主张", text(citation, "claim"));
            appendHtmlDefinition(html, "内容类型", text(citation, "contentType"));
            appendHtmlDefinition(html, "保存时核验状态", text(citation, "verificationStatus"));
            appendHtmlDefinition(html, "证据修订", scalarValues(citation.path("evidenceRevision")));
            appendHtmlDefinition(html, "保存时可用性", text(citation, "availability"));
            if (!url.isBlank()) appendHtmlDefinition(html, "链接", url);
            html.append("</dl></li>");
        }
        html.append("</ol></section>");
    }

    private void appendHtmlEvidenceCoverage(StringBuilder html, JsonNode coverage) {
        html.append("<section><h2>证据与限制</h2>")
                .append("<p>引用来自保存时授权的研究证据；来源或证据发生变化时，应重新核验后再据此决策。</p><dl>");
        for (String field : List.of(
                "status", "factClaimCount", "citedFactClaimCount", "missingEvidenceFactCount", "ratio")) {
            appendHtmlDefinition(html, field, scalarValues(coverage.path(field)));
        }
        html.append("</dl>");
        JsonNode limitations = coverage.path("limitations");
        if (limitations.isArray() && !limitations.isEmpty()) {
            html.append("<h3>已保存的限制</h3><ul>");
            for (JsonNode limitation : limitations) {
                if (limitation.isTextual()) html.append("<li>").append(htmlText(limitation.asText())).append("</li>");
            }
            html.append("</ul>");
        }
        html.append("</section>");
    }

    private void appendHtmlStructuredSnapshot(StringBuilder html, JsonNode structured) {
        html.append("<section><h2>完整结构化结果快照</h2><pre><code>")
                .append(escapeHtml(prettyJson(structured))).append("</code></pre></section>");
    }

    private void appendHtmlDefinition(StringBuilder html, String label, String value) {
        if (!StringUtils.hasText(value)) return;
        html.append("<dt>").append(escapeHtml(label)).append("</dt><dd>")
                .append(htmlText(value)).append("</dd>");
    }

    private String scalarValues(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        if (!value.isArray()) return value.isValueNode() ? value.asText() : null;
        StringBuilder joined = new StringBuilder();
        for (JsonNode item : value) {
            if (!item.isValueNode() || item.isNull()) continue;
            if (!joined.isEmpty()) joined.append(", ");
            joined.append(item.asText());
        }
        return joined.toString();
    }

    private JsonNode sanitizeStructuredSnapshot(JsonNode value) {
        if (value == null || value.isNull()) return objectMapper.getNodeFactory().nullNode();
        if (value.isArray()) {
            ArrayNode safe = objectMapper.createArrayNode();
            for (JsonNode item : value) safe.add(sanitizeStructuredSnapshot(item));
            return safe;
        }
        if (value.isObject()) {
            ObjectNode safe = objectMapper.createObjectNode();
            var fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String name = field.getKey();
                JsonNode fieldValue = field.getValue();
                String normalized = name.toLowerCase(Locale.ROOT);
                if (fieldValue.isTextual() && (normalized.endsWith("url")
                        || normalized.endsWith("href") || normalized.endsWith("link"))) {
                    safe.put(name, safeUrl(fieldValue.asText()));
                } else {
                    safe.set(name, sanitizeStructuredSnapshot(fieldValue));
                }
            }
            return safe;
        }
        return value.deepCopy();
    }

    private String prettyJson(JsonNode value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "REPORT_EXPORT_SNAPSHOT_INVALID");
        }
    }

    private String htmlText(String value) {
        if (value == null) return "";
        return escapeHtml(value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ")
                .replace("\r\n", "\n").replace('\r', '\n')).replace("\n", "<br>");
    }

    private static String pdfText(String value) {
        String decoded = value.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
                .replace("**", "");
        String escapedCharacters = "\\`*_{}[]()#+!|>";
        StringBuilder result = new StringBuilder(decoded.length());
        for (int index = 0; index < decoded.length();) {
            int codePoint = decoded.codePointAt(index);
            int width = Character.charCount(codePoint);
            if (codePoint == '\\' && index + width < decoded.length()) {
                int next = decoded.codePointAt(index + width);
                if (escapedCharacters.indexOf(next) >= 0) {
                    result.appendCodePoint(next);
                    index += width + Character.charCount(next);
                    continue;
                }
            }
            result.appendCodePoint(codePoint);
            index += width;
        }
        return result.toString();
    }

    private static final class PdfTextRenderer implements AutoCloseable {
        private static final float MARGIN = 48F;
        private static final float BODY_SIZE = 10F;
        private final PDDocument document;
        private final PDType0Font font;
        private final Map<Integer, String> unicodeByCid = new LinkedHashMap<>();
        private PDPage page;
        private PDPageContentStream content;
        private float y;

        private PdfTextRenderer(PDDocument document, PDType0Font font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void writeMarkdownLine(String line) throws IOException {
            if (line == null || line.isBlank()) {
                blank(5F);
            } else if (line.startsWith("# ")) {
                writeBlock(line.substring(2), 20F, 28F, 0F, 10F);
            } else if (line.startsWith("## ")) {
                writeBlock(line.substring(3), 15F, 21F, 9F, 5F);
            } else if (line.startsWith("### ")) {
                writeBlock(line.substring(4), 12F, 17F, 6F, 3F);
            } else if (line.startsWith("    ")) {
                writeBlock(line.substring(4), 7.5F, 10F, 0F, 0F);
            } else if (line.startsWith("> ")) {
                writeBlock(line.substring(2), BODY_SIZE, 15F, 0F, 5F);
            } else {
                writeBlock(line, BODY_SIZE, 15F, 0F, 2F);
            }
        }

        private void writeBlock(
                String rawText, float fontSize, float lineHeight, float before, float after
        ) throws IOException {
            blank(before);
            String safeText = supportedText(pdfText(rawText).replace('\t', ' '));
            for (String line : wrap(safeText, fontSize)) {
                ensureSpace(lineHeight);
                rememberUnicodeMappings(line);
                content.beginText();
                content.setFont(font, fontSize);
                content.newLineAtOffset(MARGIN, y);
                showCharacters(line);
                content.endText();
                y -= lineHeight;
            }
            blank(after);
        }

        private void showCharacters(String line) throws IOException {
            for (int offset = 0; offset < line.length();) {
                int codePoint = line.codePointAt(offset);
                content.showText(new String(Character.toChars(codePoint)));
                offset += Character.charCount(codePoint);
            }
        }

        /**
         * PDFBox derives a Type0 font's default ToUnicode map from the first
         * code point assigned to a glyph. Noto Sans SC aliases some common
         * CJK glyphs to compatibility radicals, so retain the exact Unicode
         * text sent to the content stream and publish that mapping instead.
         */
        private void rememberUnicodeMappings(String line) throws IOException {
            for (int offset = 0; offset < line.length();) {
                int codePoint = line.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                byte[] encoded = font.encode(character);
                if (encoded.length >= 2 && encoded.length % 2 == 0) {
                    for (int index = 0; index < encoded.length; index += 2) {
                        int cid = ((encoded[index] & 0xff) << 8) | (encoded[index + 1] & 0xff);
                        String previous = unicodeByCid.get(cid);
                        if (previous == null || preferredUnicode(character, previous)) {
                            unicodeByCid.put(cid, character);
                        }
                    }
                }
                offset += Character.charCount(codePoint);
            }
        }

        private boolean preferredUnicode(String candidate, String previous) {
            int candidateCodePoint = candidate.codePointAt(0);
            int previousCodePoint = previous.codePointAt(0);
            boolean candidateCompatibility = Character.UnicodeBlock.of(candidateCodePoint)
                    == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT;
            boolean previousCompatibility = Character.UnicodeBlock.of(previousCodePoint)
                    == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT;
            return previousCompatibility && !candidateCompatibility;
        }

        private void installToUnicodeMap() throws IOException {
            if (unicodeByCid.isEmpty()) return;
            COSStream stream = document.getDocument().createCOSStream();
            try (var output = stream.createOutputStream()) {
                StringBuilder cmap = new StringBuilder(256 + unicodeByCid.size() * 18);
                cmap.append("/CIDInit /ProcSet findresource begin\n")
                        .append("12 dict begin\nbegincmap\n")
                        .append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
                        .append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
                        .append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n");
                List<Map.Entry<Integer, String>> entries = new ArrayList<>(unicodeByCid.entrySet());
                for (int offset = 0; offset < entries.size();) {
                    int end = Math.min(offset + 100, entries.size());
                    cmap.append(end - offset).append(" beginbfchar\n");
                    for (int index = offset; index < end; index++) {
                        Map.Entry<Integer, String> entry = entries.get(index);
                        cmap.append('<').append(String.format(Locale.ROOT, "%04X", entry.getKey()))
                                .append("> <").append(utf16Hex(entry.getValue())).append(">\n");
                    }
                    cmap.append("endbfchar\n");
                    offset = end;
                }
                cmap.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n");
                output.write(cmap.toString().getBytes(StandardCharsets.US_ASCII));
            }
            font.getCOSObject().setItem(COSName.TO_UNICODE, stream);
        }

        private String utf16Hex(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_16BE);
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                hex.append(String.format(Locale.ROOT, "%02X", valueByte & 0xff));
            }
            return hex.toString();
        }

        private List<String> wrap(String value, float fontSize) throws IOException {
            if (value.isEmpty()) return List.of("");
            float maximumWidth = page.getMediaBox().getWidth() - (2 * MARGIN);
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            float currentWidth = 0F;
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                String glyph = new String(Character.toChars(codePoint));
                float glyphWidth = font.getStringWidth(glyph) / 1000F * fontSize;
                if (!current.isEmpty() && currentWidth + glyphWidth > maximumWidth) {
                    lines.add(current.toString().stripTrailing());
                    current.setLength(0);
                    currentWidth = 0F;
                    if (Character.isWhitespace(codePoint)) {
                        offset += Character.charCount(codePoint);
                        continue;
                    }
                }
                current.append(glyph);
                currentWidth += glyphWidth;
                offset += Character.charCount(codePoint);
            }
            if (!current.isEmpty()) lines.add(current.toString().stripTrailing());
            return lines.isEmpty() ? List.of("") : lines;
        }

        private String supportedText(String value) throws IOException {
            StringBuilder supported = new StringBuilder(value.length());
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                if (!Character.isISOControl(codePoint)) {
                    supported.appendCodePoint(codePoint);
                }
                offset += Character.charCount(codePoint);
            }
            return supported.toString();
        }

        private void blank(float height) throws IOException {
            if (height <= 0) return;
            ensureSpace(height);
            y -= height;
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (y - requiredHeight < MARGIN) newPage();
        }

        private void newPage() throws IOException {
            if (content != null) content.close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        @Override
        public void close() throws IOException {
            if (content != null) content.close();
        }
    }

    private String markdownText(String value) {
        if (value == null) return "";
        String escaped = escapeHtml(value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ")
                .replace('\r', '\n').replace('\n', ' '));
        StringBuilder result = new StringBuilder(escaped.length() + 16);
        for (int index = 0; index < escaped.length();) {
            int codePoint = escaped.codePointAt(index);
            if ("\\`*_{}[]()#+!|>".indexOf(codePoint) >= 0) result.append('\\');
            result.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void validateAuthorizedCitations(AuthenticatedUser user, Long runId, JsonNode citations) {
        Set<Long> citedSourceIds = citationSourceIds(citations);
        if (citedSourceIds.isEmpty()) return;
        if (evidenceService == null) throw citationNotAuthorized();
        Set<Long> authorizedSourceIds = evidenceService.read(user, runId).items().stream()
                .filter(item -> item.available() && item.sourceId() != null && item.sourceId() > 0)
                .map(item -> item.sourceId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!authorizedSourceIds.containsAll(citedSourceIds)) throw citationNotAuthorized();
    }

    private Set<Long> citationSourceIds(JsonNode citations) {
        if (!citations.isArray()) throw citationNotAuthorized();
        LinkedHashSet<Long> sourceIds = new LinkedHashSet<>();
        for (JsonNode citation : citations) {
            if (!citation.path("sourceId").isIntegralNumber() || citation.path("sourceId").asLong() <= 0) {
                throw citationNotAuthorized();
            }
            sourceIds.add(citation.path("sourceId").asLong());
        }
        return sourceIds;
    }

    private BusinessException citationNotAuthorized() {
        return new BusinessException(ErrorCode.CONFLICT, "REPORT_CITATION_NOT_AUTHORIZED");
    }

    private JsonNode citationManifest(JsonNode citations) {
        var manifest = objectMapper.createArrayNode();
        Set<Long> sourceIds = citationSourceIds(citations);
        Map<Long, Source> sources = sourceMapper == null || sourceIds.isEmpty() ? Map.of() : byId(sourceMapper.selectBatchIds(sourceIds));
        if (sources.size() != sourceIds.size() || sources.values().stream().anyMatch(source -> !eligible(source))) {
            throw citationNotAuthorized();
        }
        for (JsonNode citation : citations) {
            long sourceId = citation.path("sourceId").asLong();
            Source source = sources.get(sourceId);
            var item = manifest.addObject();
            item.put("sourceId", sourceId);
            item.put("claim", bounded(citation.path("claim").asText(""), 500));
            item.put("title", source == null ? "来源 #" + sourceId : bounded(source.getTitle(), 240));
            item.put("publisher", source == null ? "" : bounded(source.getPublisher(), 160));
            item.put("url", source == null ? "" : safeUrl(source.getUrl()));
            item.put("contentType", source == null ? "source" : bounded(source.getSourceType(), 80));
            item.put("verificationStatus", source == null ? "unavailable" : bounded(source.getAiEvidenceStatus(), 40));
            item.put("evidenceRevision", source == null || source.getEvidenceRevision() == null ? 0L : source.getEvidenceRevision());
            item.put("availability", eligible(source) ? "current" : "unavailable");
        }
        return manifest;
    }

    private Map<Long, Source> byId(List<Source> sources) {
        Map<Long, Source> values = new LinkedHashMap<>();
        if (sources == null) return values;
        for (Source source : sources) {
            if (source != null && source.getId() != null) values.putIfAbsent(source.getId(), source);
        }
        return values;
    }

    private boolean eligible(Source source) {
        return source != null && "published".equals(source.getStatus())
                && "verified".equals(source.getAiEvidenceStatus())
                && source.getEvidenceRevision() != null
                && StringUtils.hasText(source.getTitle())
                && StringUtils.hasText(source.getPublisher())
                && !safeUrl(source.getUrl()).isBlank();
    }

    private String safeUrl(String value) {
        if (!StringUtils.hasText(value)) return "";
        try {
            URI uri = URI.create(value.trim());
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null) {
                return "";
            }
            return uri.toString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private String bounded(String value, int maximum) {
        if (value == null) return "";
        String clean = value.trim().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        if (clean.codePointCount(0, clean.length()) <= maximum) return clean;
        return clean.substring(0, clean.offsetByCodePoints(0, maximum));
    }

    private AgentResearchReportVO toView(AgentResearchReport report) {
        return new AgentResearchReportVO(report.getId(), report.getUserId(), report.getSessionId(), report.getRunId(),
                report.getFinalMessageId(), report.getTitle(), report.getNotes(), parse(report.getResultJson()),
                parse(report.getCitationManifestJson()), report.getEvidenceVersion(), report.getDataVersion(),
                report.getSourceSessionAvailable(), report.getStatus(), report.getRevision(), report.getTrashedAt(),
                report.getPurgeAfter(), report.getCreatedAt(), report.getUpdatedAt(), evidenceState(report));
    }

    private String evidenceState(AgentResearchReport report) {
        JsonNode citations = parse(report.getCitationManifestJson());
        if (!citations.isArray() || citations.isEmpty()) return "evidence_insufficient";
        if (sourceMapper == null) return "unknown";
        LinkedHashSet<Long> sourceIds = new LinkedHashSet<>();
        for (JsonNode citation : citations) {
            if (citation.path("sourceId").isIntegralNumber() && citation.path("sourceId").asLong() > 0) {
                sourceIds.add(citation.path("sourceId").asLong());
            }
        }
        if (sourceIds.isEmpty()) return "evidence_insufficient";
        Map<Long, Source> sources = byId(sourceMapper.selectBatchIds(sourceIds));
        boolean changed = false;
        for (JsonNode citation : citations) {
            long sourceId = citation.path("sourceId").asLong(0L);
            Source source = sources.get(sourceId);
            if (!eligible(source)) return "source_unavailable";
            long savedRevision = citation.path("evidenceRevision").asLong(-1L);
            long currentRevision = source.getEvidenceRevision() == null ? 0L : source.getEvidenceRevision();
            if (savedRevision != currentRevision || !safeUrl(source.getUrl()).equals(safeUrl(citation.path("url").asText("")))) {
                changed = true;
            }
        }
        return changed ? "evidence_changed" : "current";
    }
}
