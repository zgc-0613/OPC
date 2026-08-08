package com.opc.platform.ai.controller;

import com.opc.platform.ai.dto.ResearchPreferenceUpdateDTO;
import com.opc.platform.ai.service.ResearchPreferenceService;
import com.opc.platform.ai.vo.ResearchPreferenceVO;
import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.opc.platform.userauth.UserAuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/research/preferences")
public class ResearchPreferenceController {
    private final ResearchPreferenceService service;

    @GetMapping
    public Result<ResearchPreferenceVO> read(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
        return Result.success(service.read(user));
    }

    @PatchMapping
    public Result<ResearchPreferenceVO> update(
            @RequestBody ResearchPreferenceUpdateDTO request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
        return Result.success(service.update(user, request));
    }

    @DeleteMapping
    public Result<Void> clear(
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user) {
        service.clear(user);
        return Result.success();
    }
}
