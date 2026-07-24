package com.opc.platform.ai.controller;

import com.opc.platform.ai.dto.EntrepreneurshipAdviceRequestDTO;
import com.opc.platform.ai.dto.EntrepreneurshipReadinessRequestDTO;
import com.opc.platform.ai.service.EntrepreneurshipAdvisorService;
import com.opc.platform.ai.vo.EntrepreneurshipAdviceVO;
import com.opc.platform.ai.vo.EntrepreneurshipReadinessVO;
import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.opc.platform.userauth.UserAuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class EntrepreneurshipAdvisorController {

    private final EntrepreneurshipAdvisorService advisorService;

    @PostMapping("/entrepreneurship-advice")
    public Result<EntrepreneurshipAdviceVO> advise(
            @Valid @RequestBody EntrepreneurshipAdviceRequestDTO request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(advisorService.advise(user, request));
    }

    @PostMapping("/entrepreneurship-readiness")
    public Result<EntrepreneurshipReadinessVO> readiness(
            @Valid @RequestBody EntrepreneurshipReadinessRequestDTO request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser ignoredUser
    ) {
        return Result.success(advisorService.readiness(request));
    }
}
