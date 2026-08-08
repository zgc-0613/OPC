package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.AgentResearchReportCreateDTO;
import com.opc.platform.ai.dto.AgentResearchReportRevisionDTO;
import com.opc.platform.ai.dto.AgentResearchReportUpdateDTO;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.mapper.AgentResearchReportMapper;
import com.opc.platform.ai.entity.AgentResearchReport;
import com.opc.platform.ai.vo.AgentEvidenceItemVO;
import com.opc.platform.ai.vo.AgentRunEvidenceVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentResearchReportServiceTest {

    private final AuthenticatedUser user = new AuthenticatedUser(42L, "owner", "owner@example.com");

    @Test
    void idempotencyReplayRejectsARequestForDifferentReportMaterial() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        AgentResearchReport existing = new AgentResearchReport();
        existing.setId(7L); existing.setUserId(42L); existing.setSessionId(10L);
        existing.setFinalMessageId(501L); existing.setTitle("Original title"); existing.setNotes("Original notes");
        existing.setResultJson("{}"); existing.setCitationManifestJson("[]");
        when(reports.findByUserAndIdempotency(42L, "report-idem-replay")).thenReturn(existing);
        AgentResearchReportService service = new AgentResearchReportService(
                runs, messages, reports, new ObjectMapper());
        AgentResearchReportCreateDTO request = createRequest("report-idem-replay");
        request.setTitle("Different title");
        request.setNotes("Original notes");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.save(user, 10L, request));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("REPORT_IDEMPOTENCY_CONFLICT", exception.getMessage());
        verify(runs, never()).selectOwnedAgentRunByFinalMessage(any(), any());
    }

    @Test
    void reportPageReturnsAnOpaqueStableCursorAndDoesNotExposeTheExtraRow() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        LocalDateTime snapshot = LocalDateTime.of(2026, 8, 2, 6, 0);
        AgentResearchReport newest = reportRow(9L, snapshot.minusMinutes(1));
        AgentResearchReport visible = reportRow(8L, snapshot.minusMinutes(2));
        AgentResearchReport extra = reportRow(7L, snapshot.minusMinutes(3));
        when(reports.selectCurrentTimestamp()).thenReturn(snapshot);
        when(reports.listOwnedPage(eq(42L), eq("active"), eq(null), eq(snapshot),
                eq(null), eq(null), eq(3))).thenReturn(List.of(newest, visible, extra));
        AgentResearchReportService service = new AgentResearchReportService(
                runs, messages, reports, new ObjectMapper());

        var page = service.page(user, "active", "", null, 2);

        assertEquals(List.of(9L, 8L), page.items().stream().map(item -> item.reportId()).toList());
        assertTrue(page.hasMore());
        assertTrue(page.nextCursor() != null && !page.nextCursor().isBlank());
    }

    @Test
    void savesOnlyOwnedCompletedRunAndCopiesEvidenceMetadata() throws Exception {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        AiAnalysisRun run = completedRun();
        AiAgentMessage message = finalMessage();
        when(runs.selectOwnedAgentRunByFinalMessage(501L, 42L)).thenReturn(run);
        when(messages.selectFinalByRun(91L)).thenReturn(message);
        when(reports.findByUserAndIdempotency(42L, "report-idem-1")).thenReturn(null);
        when(reports.insert(any(AgentResearchReport.class))).thenAnswer(invocation -> {
            AgentResearchReport report = invocation.getArgument(0);
            report.setId(7L);
            return 1;
        });

        SourceMapper sources = mock(SourceMapper.class);
        Source source = new Source();
        source.setId(8L); source.setTitle("审核来源"); source.setPublisher("发布单位");
        source.setUrl("https://example.org/source"); source.setStatus("published");
        source.setAiEvidenceStatus("verified"); source.setEvidenceRevision(3L);
        when(sources.selectBatchIds(java.util.Set.of(8L))).thenReturn(List.of(source));
        AgentRunEvidenceService evidence = mock(AgentRunEvidenceService.class);
        when(evidence.read(user, 91L)).thenReturn(runEvidence(8L, true));
        AgentResearchReportService service = new AgentResearchReportService(
                runs, messages, reports, new ObjectMapper(), sources, evidence);
        AgentResearchReportCreateDTO request = new AgentResearchReportCreateDTO();
        request.setFinalMessageId(501L);
        request.setTitle("我的研究报告");
        request.setNotes("用于团队评审");
        request.setIdempotencyKey("report-idem-1");

        var result = service.save(user, 10L, request);

        assertEquals(7L, result.reportId());
        assertEquals("active", result.status());
        assertEquals("evidence-v1", result.evidenceVersion());
        assertEquals(91L, result.runId());
        assertEquals("审核来源", result.citationManifest().get(0).path("title").asText());
        assertEquals("https://example.org/source", result.citationManifest().get(0).path("url").asText());
        verify(reports).insert(any(AgentResearchReport.class));
    }

    @Test
    void rejectsCitationOutsideTheRunAuthorizedEvidence() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        AgentRunEvidenceService evidence = mock(AgentRunEvidenceService.class);
        AiAnalysisRun run = completedRun();
        AiAgentMessage message = finalMessage();
        message.setCitationsJson("[{\"sourceId\":9,\"claim\":\"unauthorized\"}]");
        when(runs.selectOwnedAgentRunByFinalMessage(501L, 42L)).thenReturn(run);
        when(messages.selectFinalByRun(91L)).thenReturn(message);
        when(evidence.read(user, 91L)).thenReturn(runEvidence(8L, true));
        AgentResearchReportService service = new AgentResearchReportService(
                runs, messages, reports, new ObjectMapper(), sources, evidence);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.save(user, 10L, createRequest("report-idem-unauthorized")));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("REPORT_CITATION_NOT_AUTHORIZED", exception.getMessage());
        verify(reports, never()).insert(any(AgentResearchReport.class));
        verify(sources, never()).selectBatchIds(any());
    }

    @Test
    void rejectsCitationWhoseAuthorizedSourceIsNoLongerEligible() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        AgentRunEvidenceService evidence = mock(AgentRunEvidenceService.class);
        when(runs.selectOwnedAgentRunByFinalMessage(501L, 42L)).thenReturn(completedRun());
        when(messages.selectFinalByRun(91L)).thenReturn(finalMessage());
        when(evidence.read(user, 91L)).thenReturn(runEvidence(8L, false));
        AgentResearchReportService service = new AgentResearchReportService(
                runs, messages, reports, new ObjectMapper(), sources, evidence);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.save(user, 10L, createRequest("report-idem-withdrawn")));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("REPORT_CITATION_NOT_AUTHORIZED", exception.getMessage());
        verify(reports, never()).insert(any(AgentResearchReport.class));
    }

    @Test
    void rejectsRunWithoutCompletedFinalMessageOrEvidenceVersion() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        AiAnalysisRun run = completedRun();
        run.setResultJson("{\"finalMessageId\":501,\"structuredResult\":{}}");
        when(runs.selectOwnedAgentRunByFinalMessage(501L, 42L)).thenReturn(run);
        when(messages.selectFinalByRun(91L)).thenReturn(finalMessage());
        AgentResearchReportService service = new AgentResearchReportService(runs, messages, reports, new ObjectMapper());
        AgentResearchReportCreateDTO request = new AgentResearchReportCreateDTO();
        request.setFinalMessageId(501L);
        request.setTitle("报告");
        request.setIdempotencyKey("report-idem-2");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.save(user, 10L, request));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verify(reports, never()).insert(any(AgentResearchReport.class));
    }

    @Test
    void lifecycleUsesCompareAndSetAndRejectsStaleRevision() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        AgentResearchReport report = new AgentResearchReport();
        report.setId(7L); report.setUserId(42L); report.setStatus("active"); report.setRevision(3L);
        when(reports.selectOwned(7L, 42L)).thenReturn(report);
        AgentResearchReportService service = new AgentResearchReportService(runs, messages, reports, new ObjectMapper());
        AgentResearchReportRevisionDTO request = new AgentResearchReportRevisionDTO();
        request.setExpectedRevision(2L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.trash(user, 7L, request));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("REPORT_REVISION_CONFLICT", exception.getMessage());
        verify(reports, never()).trash(eq(7L), eq(42L), any(), any(), any());
    }

    @Test
    void updatesActiveReportMetadataWithCompareAndSet() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        AgentResearchReport report = new AgentResearchReport();
        report.setId(7L); report.setUserId(42L); report.setStatus("active"); report.setRevision(3L);
        report.setResultJson("{}"); report.setCitationManifestJson("[]");
        when(reports.selectOwned(7L, 42L)).thenReturn(report);
        when(reports.updateMetadata(eq(7L), eq(42L), eq(3L), eq("Updated title"), eq("Updated notes"), any())).thenReturn(1);
        AgentResearchReportService service = new AgentResearchReportService(runs, messages, reports, new ObjectMapper());
        AgentResearchReportUpdateDTO request = new AgentResearchReportUpdateDTO();
        request.setExpectedRevision(3L); request.setTitle("Updated title"); request.setNotes("Updated notes");

        var updated = service.update(user, 7L, request);

        assertEquals("Updated title", updated.title());
        assertEquals("Updated notes", updated.notes());
        assertEquals(4L, updated.revision());
        verify(reports).updateMetadata(eq(7L), eq(42L), eq(3L), eq("Updated title"), eq("Updated notes"), any());
    }

    @Test
    void preservesTheFrozenManifestButMarksEvidenceChangedWhenASourceRevisionDrifts() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        SourceMapper sources = mock(SourceMapper.class);
        AgentResearchReport report = new AgentResearchReport();
        report.setId(7L); report.setUserId(42L); report.setStatus("active"); report.setRevision(1L);
        report.setResultJson("{\"structuredResult\":{}}");
        report.setCitationManifestJson("[{\"sourceId\":8,\"evidenceRevision\":2,\"url\":\"https://example.org/source\",\"availability\":\"current\"}]");
        report.setEvidenceVersion("evidence-v1");
        Source current = new Source();
        current.setId(8L); current.setStatus("published"); current.setAiEvidenceStatus("verified");
        current.setTitle("Verified source"); current.setPublisher("Publisher");
        current.setUrl("https://example.org/source"); current.setEvidenceRevision(3L);
        when(reports.selectOwned(7L, 42L)).thenReturn(report);
        when(sources.selectBatchIds(java.util.Set.of(8L))).thenReturn(List.of(current));
        AgentResearchReportService service = new AgentResearchReportService(runs, messages, reports, new ObjectMapper(), sources);

        var view = service.get(user, 7L);

        assertEquals("evidence_changed", view.evidenceState());
        assertEquals(2L, view.citationManifest().get(0).path("evidenceRevision").asLong());
    }

    @Test
    void htmlExportEscapesStoredContentAndIncludesResearchProvenance() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        AgentResearchReport report = new AgentResearchReport();
        report.setId(7L); report.setUserId(42L); report.setStatus("active");
        report.setTitle("<script>alert(1)</script>Research");
        report.setResultJson("{\"structuredResult\":{\"directAnswer\":\"<img src=x onerror=alert(1)>\",\"evidenceCoverage\":{\"limitations\":[\"limited source coverage\"]}}}");
        report.setCitationManifestJson("[{\"sourceId\":8,\"claim\":\"Verified source\"}]");
        report.setEvidenceVersion("sha256:version"); report.setCreatedAt(LocalDateTime.now());
        when(reports.selectOwned(7L, 42L)).thenReturn(report);
        AgentResearchReportService service = new AgentResearchReportService(runs, messages, reports, new ObjectMapper());

        String html = service.exportHtml(user, 7L);

        assertTrue(html.contains("AI-assisted research result"));
        assertTrue(html.contains("sha256:version"));
        assertTrue(html.contains("Verified source"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertFalse(html.contains("<script>alert(1)</script>"));
    }

    @Test
    void markdownExportIncludesStoredSourcesWithoutPreservingUnsafeMarkupOrUrls() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        AgentResearchReport report = new AgentResearchReport();
        report.setId(7L); report.setUserId(42L); report.setStatus("active");
        report.setTitle("Research report");
        report.setResultJson("{\"structuredResult\":{\"summary\":\"<img src=x onerror=alert(1)>\"}}");
        report.setCitationManifestJson("[{\"sourceId\":8,\"title\":\"<b>Verified source</b>\",\"publisher\":\"Publisher\",\"url\":\"javascript:alert(1)\",\"claim\":\"<script>alert(1)</script>\"}]");
        report.setEvidenceVersion("sha256:version"); report.setCreatedAt(LocalDateTime.now());
        when(reports.selectOwned(7L, 42L)).thenReturn(report);
        AgentResearchReportService service = new AgentResearchReportService(runs, messages, reports, new ObjectMapper());

        String markdown = service.exportMarkdown(user, 7L);

        assertTrue(markdown.contains("## Sources"));
        assertTrue(markdown.contains("Verified source"));
        assertFalse(markdown.contains("<img src=x onerror=alert(1)>"));
        assertFalse(markdown.contains("<script>alert(1)</script>"));
        assertFalse(markdown.contains("javascript:alert(1)"));
    }

    @Test
    void scheduledPurgeOnlyErasesDueTrashedReportsWithTheirCurrentRevision() {
        AiAnalysisRunMapper runs = mock(AiAnalysisRunMapper.class);
        AiAgentMessageMapper messages = mock(AiAgentMessageMapper.class);
        AgentResearchReportMapper reports = mock(AgentResearchReportMapper.class);
        AgentResearchReport due = new AgentResearchReport();
        due.setId(7L); due.setUserId(42L); due.setStatus("trash"); due.setRevision(3L);
        due.setResultJson("{\"structuredResult\":{}}"); due.setCitationManifestJson("[]");
        due.setNotes("temporary"); due.setPurgeAfter(LocalDateTime.now().minusMinutes(1));
        when(reports.selectDueForPurge(any(), eq(20))).thenReturn(List.of(due));
        when(reports.permanentlyPurgeDue(eq(7L), eq(3L), any())).thenReturn(1);
        AgentResearchReportService service = new AgentResearchReportService(runs, messages, reports, new ObjectMapper());

        assertEquals(1, service.purgeDue());
        assertEquals("permanently_purged", due.getStatus());
        assertEquals(null, due.getResultJson());
        verify(reports).permanentlyPurgeDue(eq(7L), eq(3L), any());
    }

    private AiAnalysisRun completedRun() {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(91L); run.setUserId(42L); run.setSessionId(10L); run.setStatus("completed");
        run.setResultJson("{\"resultVersion\":\"agent-research-v2\",\"structuredResult\":{\"evidenceVersion\":\"evidence-v1\",\"dataVersion\":null,\"summary\":\"摘要\",\"citations\":[{\"sourceId\":8,\"claim\":\"事实\"}]}}");
        return run;
    }

    private AgentResearchReport reportRow(Long id, LocalDateTime updatedAt) {
        AgentResearchReport report = new AgentResearchReport();
        report.setId(id); report.setUserId(42L); report.setSessionId(10L); report.setRunId(91L);
        report.setFinalMessageId(501L); report.setTitle("Report " + id); report.setStatus("active");
        report.setRevision(1L); report.setResultJson("{}"); report.setCitationManifestJson("[]");
        report.setUpdatedAt(updatedAt); report.setCreatedAt(updatedAt);
        return report;
    }

    private AgentResearchReportCreateDTO createRequest(String idempotencyKey) {
        AgentResearchReportCreateDTO request = new AgentResearchReportCreateDTO();
        request.setFinalMessageId(501L);
        request.setTitle("Research report");
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private AgentRunEvidenceVO runEvidence(Long sourceId, boolean available) {
        AgentEvidenceItemVO item = new AgentEvidenceItemVO(
                "source", sourceId, sourceId, "Source", "", "", "", "", "",
                available ? "verified" : "unavailable", "Publisher", "Source",
                available ? "https://example.org/source" : null, null, available);
        return new AgentRunEvidenceVO(91L, "completed", List.of(item), Map.of("source", 1),
                available ? 1 : 0, 1, available ? 0 : 1,
                Map.of("source", available ? 1 : 0), Map.of("source", 1));
    }

    private AiAgentMessage finalMessage() {
        AiAgentMessage message = new AiAgentMessage();
        message.setId(501L); message.setRunId(91L); message.setRole("assistant");
        message.setStatus("completed"); message.setContent("研究结果");
        message.setCitationsJson("[{\"sourceId\":8,\"claim\":\"事实\"}]");
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
}
