package com.opc.platform.userauth.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.dto.PasswordLoginDTO;
import com.opc.platform.userauth.dto.RegisterUserDTO;
import com.opc.platform.userauth.dto.SendEmailCodeDTO;
import com.opc.platform.userauth.service.UserAuthService;
import com.opc.platform.userauth.service.AltchaService;
import com.opc.platform.settings.service.CaptchaSettingsService;
import com.opc.platform.userauth.vo.AltchaConfigVO;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class UserAuthController {

    private final UserAuthService userAuthService;
    private final AltchaService altchaService;
    private final CaptchaSettingsService captchaSettingsService;

    @GetMapping("/altcha/config")
    public Result<AltchaConfigVO> getAltchaConfig() {
        return Result.success(new AltchaConfigVO(captchaSettingsService.enabled()));
    }

    @GetMapping(value = "/altcha/challenge", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAltchaChallenge() {
        return ResponseEntity.ok(altchaService.createChallenge().toJson());
    }

    @PostMapping("/email-code")
    public Result<SendEmailCodeVO> sendEmailCode(@Valid @RequestBody SendEmailCodeDTO dto) {
        return Result.success(userAuthService.sendEmailCode(dto));
    }

    @PostMapping("/register")
    public Result<UserLoginVO> register(@Valid @RequestBody RegisterUserDTO dto) {
        return Result.success(userAuthService.registerWithEmailCode(dto));
    }

    @PostMapping("/login")
    public Result<UserLoginVO> login(@Valid @RequestBody PasswordLoginDTO dto) {
        return Result.success(userAuthService.loginWithPassword(dto));
    }

    @PostMapping("/verify")
    public Result<UserLoginVO> rejectLegacyEmailCodeLogin() {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱验证码登录已停用，请使用密码登录");
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
