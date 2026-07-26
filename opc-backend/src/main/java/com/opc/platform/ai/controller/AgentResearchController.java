package com.opc.platform.ai.controller;

import com.opc.platform.ai.dto.AgentMessageCreateDTO;
import com.opc.platform.ai.dto.AgentSessionCreateDTO;
import com.opc.platform.ai.dto.AgentSessionStartDTO;
import com.opc.platform.ai.dto.AgentSessionUpdateDTO;
import com.opc.platform.ai.service.AgentResearchReceipt;
import com.opc.platform.ai.service.AgentResearchQueryService;
import com.opc.platform.ai.service.AgentResearchService;
import com.opc.platform.ai.service.AgentResearchStartReceipt;
import com.opc.platform.ai.service.AgentSessionHistoryService;
import com.opc.platform.ai.service.AgentRunEvidenceService;
import com.opc.platform.ai.vo.AgentRunStatusVO;
import com.opc.platform.ai.vo.AgentSessionDetailVO;
import com.opc.platform.ai.vo.AgentSessionVO;
import com.opc.platform.ai.vo.AgentSessionHistoryPageVO;
import com.opc.platform.ai.vo.AgentMessagePageVO;
import com.opc.platform.ai.vo.AgentUsageVO;
import com.opc.platform.ai.vo.AgentRunEvidenceVO;
import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.opc.platform.userauth.UserAuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/research")
public class AgentResearchController {

    private final AgentResearchService researchService;
    private final AgentResearchQueryService queryService;
    private final AgentSessionHistoryService historyService;
    private final AgentRunEvidenceService evidenceService;

    @PostMapping("/sessions")
    public Result<AgentSessionVO> createSession(
            @Valid @RequestBody AgentSessionCreateDTO request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(queryService.createSession(user, request));
    }

    @PostMapping("/sessions/start")
    public ResponseEntity<Result<AgentResearchStartReceipt>> startSession(
            @Valid @RequestBody AgentSessionStartDTO request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return ResponseEntity.accepted().body(Result.success(researchService.start(user, request)));
    }

    @GetMapping("/sessions")
    public Result<List<AgentSessionVO>> sessions(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(queryService.listSessions(user));
    }

    @GetMapping("/sessions/history")
    public Result<AgentSessionHistoryPageVO> history(
            @RequestParam(defaultValue = "active") String scope,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(historyService.history(user, scope, q, cursor, limit));
    }

    @GetMapping("/sessions/{sessionId}")
    public Result<AgentSessionDetailVO> session(
            @PathVariable Long sessionId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(queryService.sessionDetail(user, sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> archive(
            @PathVariable Long sessionId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        historyService.archive(user, sessionId);
        return Result.success();
    }

    @PatchMapping("/sessions/{sessionId}")
    public Result<AgentSessionVO> updateSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody AgentSessionUpdateDTO request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(historyService.view(historyService.update(user, sessionId, request)));
    }

    @PostMapping("/sessions/{sessionId}/archive")
    public Result<AgentSessionVO> archiveExplicit(
            @PathVariable Long sessionId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(historyService.view(historyService.archive(user, sessionId)));
    }

    @PostMapping("/sessions/{sessionId}/unarchive")
    public Result<AgentSessionVO> unarchive(
            @PathVariable Long sessionId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(historyService.view(historyService.unarchive(user, sessionId)));
    }

    @PostMapping("/sessions/{sessionId}/trash")
    public Result<AgentSessionVO> trash(
            @PathVariable Long sessionId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(historyService.view(historyService.trash(user, sessionId)));
    }

    @PostMapping("/sessions/{sessionId}/restore")
    public Result<AgentSessionVO> restore(
            @PathVariable Long sessionId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(historyService.view(historyService.restore(user, sessionId)));
    }

    @DeleteMapping("/sessions/{sessionId}/permanent")
    public Result<Void> purge(
            @PathVariable Long sessionId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        historyService.purge(user, sessionId);
        return Result.success();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<AgentMessagePageVO> messages(
            @PathVariable Long sessionId,
            @RequestParam(required = false) Integer beforeSequence,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(historyService.messages(user, sessionId, beforeSequence, limit));
    }

    @GetMapping("/usage")
    public Result<AgentUsageVO> usage(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(historyService.usage(user));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<Result<AgentResearchReceipt>> submit(
            @PathVariable Long sessionId,
            @Valid @RequestBody AgentMessageCreateDTO request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return ResponseEntity.accepted().body(Result.success(researchService.submit(user, sessionId, request)));
    }

    @GetMapping("/runs/{runId}")
    public Result<AgentRunStatusVO> run(
            @PathVariable Long runId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(queryService.run(user, runId));
    }

    @GetMapping("/runs/{runId}/evidence")
    public Result<AgentRunEvidenceVO> evidence(
            @PathVariable Long runId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(evidenceService.read(user, runId));
    }

    @PostMapping("/runs/{runId}/cancel")
    public Result<AgentRunStatusVO> cancel(
            @PathVariable Long runId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(queryService.cancel(user, runId));
    }
}
