package com.opc.platform.userauth.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.dto.SendEmailCodeDTO;
import com.opc.platform.userauth.dto.VerifyEmailLoginDTO;
import com.opc.platform.userauth.service.UserAuthService;
import com.opc.platform.userauth.vo.SendEmailCodeVO;
import com.opc.platform.userauth.vo.UserLoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class UserAuthController {

    private final UserAuthService userAuthService;

    @PostMapping("/email-code")
    public Result<SendEmailCodeVO> sendEmailCode(@Valid @RequestBody SendEmailCodeDTO dto) {
        return Result.success(userAuthService.sendEmailCode(dto));
    }

    @PostMapping("/verify")
    public Result<UserLoginVO> verifyEmailCodeAndLogin(@Valid @RequestBody VerifyEmailLoginDTO dto) {
        return Result.success(userAuthService.verifyEmailCodeAndLogin(dto));
    }

    @GetMapping("/me")
    public Result<UserLoginVO> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(userAuthService.getCurrentUser(extractBearerToken(authorization)));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = extractBearerToken(authorization);
        if (StringUtils.hasText(token)) {
            userAuthService.logout(token);
        }
        return Result.success();
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
