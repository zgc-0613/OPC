package com.opc.platform.ai.controller;

import com.opc.platform.ai.dto.AgentRunFeedbackUpdateDTO;
import com.opc.platform.ai.service.AgentRunFeedbackService;
import com.opc.platform.ai.vo.AgentRunFeedbackVO;
import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.opc.platform.userauth.UserAuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/research/runs")
public class AgentRunFeedbackController {

    private final AgentRunFeedbackService feedback;

    @GetMapping("/{runId}/feedback")
    public Result<AgentRunFeedbackVO> get(
            @PathVariable Long runId,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(feedback.get(user, runId));
    }

    @PutMapping("/{runId}/feedback")
    public Result<AgentRunFeedbackVO> upsert(
            @PathVariable Long runId,
            @RequestBody AgentRunFeedbackUpdateDTO request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(feedback.upsert(user, runId, request));
    }
}
