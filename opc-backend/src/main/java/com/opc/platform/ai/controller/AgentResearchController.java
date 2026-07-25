package com.opc.platform.ai.controller;

import com.opc.platform.ai.dto.AgentMessageCreateDTO;
import com.opc.platform.ai.dto.AgentSessionCreateDTO;
import com.opc.platform.ai.service.AgentResearchReceipt;
import com.opc.platform.ai.service.AgentResearchQueryService;
import com.opc.platform.ai.service.AgentResearchService;
import com.opc.platform.ai.vo.AgentRunStatusVO;
import com.opc.platform.ai.vo.AgentSessionDetailVO;
import com.opc.platform.ai.vo.AgentSessionVO;
import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/sessions")
    public Result<AgentSessionVO> createSession(
            @Valid @RequestBody AgentSessionCreateDTO request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(queryService.createSession(user, request));
    }

    @GetMapping("/sessions")
    public Result<List<AgentSessionVO>> sessions(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(queryService.listSessions(user));
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
        queryService.archiveSession(user, sessionId);
        return Result.success();
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

    @PostMapping("/runs/{runId}/cancel")
    public Result<AgentRunStatusVO> cancel(
            @PathVariable Long runId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(queryService.cancel(user, runId));
    }
}
