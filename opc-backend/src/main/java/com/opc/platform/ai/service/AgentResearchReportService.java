package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.List;

@Service
public class AgentResearchReportService {

    private static final Pattern HTML_TAG = Pattern.compile("<\\/?[a-zA-Z][^>]*>");
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
        report.setResultJson(write(result));
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

    public String exportMarkdown(AuthenticatedUser user, Long reportId) {
        AgentResearchReport report = requireOwned(user, reportId);
        if (!"active".equals(report.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "回收站中的报告不能导出");
        JsonNode result = parse(report.getResultJson());
        StringBuilder markdown = new StringBuilder();
        markdown.append("# " ).append(markdownText(report.getTitle())).append("\n\n");
        markdown.append("> AI 辅助研究结果。证据版本：").append(markdownText(report.getEvidenceVersion())).append("\n\n");
        if (report.getDataVersion() != null) markdown.append("> 数据版本：").append(markdownText(report.getDataVersion())).append("\n\n");
        JsonNode structured = result.path("structuredResult");
        String summary = text(structured, "directAnswer");
        if (summary == null) summary = text(structured, "summary");
        if (summary != null) markdown.append("## 摘要\n\n").append(markdownText(summary)).append("\n\n");
        appendMarkdownSources(markdown, parse(report.getCitationManifestJson()));
        markdown.append("## 证据与限制\n\n");
        markdown.append("引用来自保存时的 Run allowlist；若来源后续失效，历史报告会标记为需重新核验。\n\n");
        markdown.append("生成时间：").append(markdownText(String.valueOf(report.getCreatedAt()))).append("\n");
        return markdown.toString();
    }

    public String exportHtml(AuthenticatedUser user, Long reportId) {
        AgentResearchReport report = requireOwned(user, reportId);
        if (!"active".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Reports in trash cannot be exported");
        }
        JsonNode result = parse(report.getResultJson());
        JsonNode structured = result.path("structuredResult");
        String summary = text(structured, "directAnswer");
        if (summary == null) summary = text(structured, "summary");
        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
                .append("<title>").append(escapeHtml(report.getTitle())).append("</title></head><body>")
                .append("<h1>").append(escapeHtml(report.getTitle())).append("</h1>")
                .append("<p><strong>AI-assisted research result</strong></p>")
                .append("<dl><dt>Evidence version</dt><dd>").append(escapeHtml(report.getEvidenceVersion())).append("</dd>")
                .append("<dt>Data version</dt><dd>").append(escapeHtml(report.getDataVersion())).append("</dd>")
                .append("<dt>Generated at</dt><dd>").append(escapeHtml(String.valueOf(report.getCreatedAt()))).append("</dd></dl>");
        if (summary != null) html.append("<h2>Summary</h2><p>").append(escapeHtml(summary)).append("</p>");
        if (report.getNotes() != null) html.append("<h2>Notes</h2><p>").append(escapeHtml(report.getNotes())).append("</p>");
        html.append("<h2>Sources</h2><ol>");
        JsonNode citations = parse(report.getCitationManifestJson());
        if (citations.isArray()) {
            for (JsonNode citation : citations) {
                String title = citation.path("title").asText("Source #" + citation.path("sourceId").asLong());
                String publisher = citation.path("publisher").asText("");
                String url = safeUrl(citation.path("url").asText(""));
                html.append("<li>");
                if (!url.isBlank()) {
                    html.append("<a rel=\"noopener noreferrer\" href=\"").append(escapeHtml(url)).append("\">")
                            .append(escapeHtml(title)).append("</a>");
                } else {
                    html.append(escapeHtml(title));
                }
                if (!publisher.isBlank()) html.append(" <small>").append(escapeHtml(publisher)).append("</small>");
                html.append(": ").append(escapeHtml(citation.path("claim").asText(""))).append("</li>");
            }
        }
        html.append("</ol><h2>Evidence limits</h2>")
                .append("<p>Citations come from the stored Run allowlist. Re-check this report when a source is withdrawn or evidence changes.</p><ul>");
        JsonNode limitations = structured.path("evidenceCoverage").path("limitations");
        if (limitations.isArray()) {
            for (JsonNode limitation : limitations) {
                if (limitation.isTextual()) html.append("<li>").append(escapeHtml(limitation.asText())).append("</li>");
            }
        }
        return html.append("</ul></body></html>").toString();
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

    private void appendMarkdownSources(StringBuilder markdown, JsonNode citations) {
        markdown.append("## Sources\n\n");
        if (!citations.isArray() || citations.isEmpty()) {
            markdown.append("No stored sources were available for this report.\n\n");
            return;
        }
        for (JsonNode citation : citations) {
            String title = citation.path("title").asText("Source #" + citation.path("sourceId").asLong());
            String publisher = citation.path("publisher").asText("");
            String url = safeUrl(citation.path("url").asText(""));
            String claim = citation.path("claim").asText("");
            markdown.append("- ").append(markdownText(title));
            if (!publisher.isBlank()) markdown.append(" (").append(markdownText(publisher)).append(")");
            if (!url.isBlank()) markdown.append(": <").append(url).append(">");
            if (!claim.isBlank()) markdown.append("\n  ").append(markdownText(claim));
            markdown.append("\n");
        }
        markdown.append("\n");
    }

    private String markdownText(String value) {
        if (value == null) return "";
        String escaped = escapeHtml(value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ")
                .replace('\r', '\n').replace('\n', ' '));
        StringBuilder result = new StringBuilder(escaped.length() + 16);
        for (int index = 0; index < escaped.length();) {
            int codePoint = escaped.codePointAt(index);
            if ("\\`*_{}[]()#+-.!|>".indexOf(codePoint) >= 0) result.append('\\');
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
