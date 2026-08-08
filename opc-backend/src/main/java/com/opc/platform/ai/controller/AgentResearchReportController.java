package com.opc.platform.ai.controller;

import com.opc.platform.ai.dto.AgentResearchReportCreateDTO;
import com.opc.platform.ai.dto.AgentResearchReportRevisionDTO;
import com.opc.platform.ai.dto.AgentResearchReportUpdateDTO;
import com.opc.platform.ai.service.AgentResearchReportService;
import com.opc.platform.ai.vo.AgentResearchReportVO;
import com.opc.platform.ai.vo.AgentResearchReportPageVO;
import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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

    @GetMapping(value = "/reports/{reportId}/export", params = "format=html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> exportHtml(
            @PathVariable Long reportId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .header("Content-Disposition", "attachment; filename*=UTF-8''research-report.html")
                .body(reports.exportHtml(user, reportId));
    }

    @GetMapping(value = "/reports/{reportId}/export", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> export(@PathVariable Long reportId, @RequestParam(defaultValue = "markdown") String format, @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (!"markdown".equalsIgnoreCase(format)) throw new com.opc.platform.common.exception.BusinessException(com.opc.platform.common.enums.ErrorCode.BAD_REQUEST, "当前仅支持 Markdown 导出");
        return ResponseEntity.ok().contentType(MediaType.TEXT_MARKDOWN).header("Content-Disposition", "attachment; filename*=UTF-8''research-report.md").body(reports.exportMarkdown(user, reportId));
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
