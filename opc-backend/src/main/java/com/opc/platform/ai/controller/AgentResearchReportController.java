package com.opc.platform.ai.controller;

import com.opc.platform.ai.dto.AgentResearchReportCreateDTO;
import com.opc.platform.ai.dto.AgentResearchReportRevisionDTO;
import com.opc.platform.ai.dto.AgentResearchReportUpdateDTO;
import com.opc.platform.ai.service.AgentResearchReportService;
import com.opc.platform.ai.vo.AgentResearchReportVO;
import com.opc.platform.ai.vo.AgentResearchReportPageVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

import static com.opc.platform.userauth.UserAuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/research")
public class AgentResearchReportController {
    private final AgentResearchReportService reports;

    @PostMapping("/sessions/{sessionId}/reports")
    public Result<AgentResearchReportVO> save(@PathVariable Long sessionId, @Valid @RequestBody AgentResearchReportCreateDTO request, @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
        return Result.success(reports.save(user, sessionId, request));
    }

    @GetMapping("/reports/{reportId}")
    public Result<AgentResearchReportVO> get(@PathVariable Long reportId, @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
        return Result.success(reports.get(user, reportId));
    }

    @PatchMapping("/reports/{reportId}")
    public Result<AgentResearchReportVO> update(
            @PathVariable Long reportId,
            @Valid @RequestBody AgentResearchReportUpdateDTO request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(reports.update(user, reportId, request));
    }

    @GetMapping("/reports")
    public Result<java.util.List<AgentResearchReportVO>> list(@RequestParam(defaultValue = "active") String scope, @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "30") int limit, @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
        return Result.success(reports.list(user, scope, q, limit));
    }

    @GetMapping("/reports/page")
    public Result<AgentResearchReportPageVO> page(
            @RequestParam(defaultValue = "active") String scope,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(reports.page(user, scope, q, cursor, limit));
    }

    @GetMapping("/reports/{reportId}/export")
    public ResponseEntity<?> export(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "markdown") String format,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        String normalized = format == null ? "markdown" : format.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "markdown" -> ResponseEntity.ok()
                    .contentType(MediaType.TEXT_MARKDOWN)
                    .header("Content-Disposition", "attachment; filename*=UTF-8''research-report.md")
                    .header("Cache-Control", "private, no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(reports.exportMarkdown(user, reportId));
            case "html" -> ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .header("Content-Disposition", "attachment; filename*=UTF-8''research-report.html")
                    .header("Cache-Control", "private, no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(reports.exportHtml(user, reportId));
            case "pdf" -> ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "attachment; filename*=UTF-8''research-report.pdf")
                    .header("Cache-Control", "private, no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(reports.exportPdf(user, reportId));
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "REPORT_EXPORT_FORMAT_INVALID");
        };
    }

    @PostMapping("/reports/{reportId}/trash")
    public Result<AgentResearchReportVO> trash(@PathVariable Long reportId, @Valid @RequestBody AgentResearchReportRevisionDTO request, @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
        return Result.success(reports.trash(user, reportId, request));
    }

    @PostMapping("/reports/{reportId}/restore")
    public Result<AgentResearchReportVO> restore(@PathVariable Long reportId, @Valid @RequestBody AgentResearchReportRevisionDTO request, @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
        return Result.success(reports.restore(user, reportId, request));
    }

    @DeleteMapping("/reports/{reportId}/permanent")
    public Result<AgentResearchReportVO> permanent(@PathVariable Long reportId, @Valid @RequestBody AgentResearchReportRevisionDTO request, @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
        return Result.success(reports.permanent(user, reportId, request));
    }
}
