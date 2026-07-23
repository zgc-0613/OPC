package com.opc.platform.ai.controller;

import com.opc.platform.ai.dto.CaseAnalysisRequestDTO;
import com.opc.platform.ai.service.CaseAnalysisService;
import com.opc.platform.ai.vo.CaseAnalysisVO;
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
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class CaseAnalysisController {

    private final CaseAnalysisService analysisService;

    @PostMapping("/case-analysis")
    public Result<CaseAnalysisVO> analyze(
            @Valid @RequestBody CaseAnalysisRequestDTO request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return Result.success(analysisService.analyze(user, request));
    }
}
