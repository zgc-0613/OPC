package com.opc.platform.userauth.service;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.settings.service.CaptchaSettingsService;
import lombok.RequiredArgsConstructor;
import org.altcha.altcha.v2.Altcha;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AltchaService {

    private static final String ACTION_REGISTER = "register";
    private static final int MAX_PAYLOAD_LENGTH = 16_384;

    private final CaptchaSettingsService captchaSettingsService;
    private final ConcurrentHashMap<String, Long> consumedPayloads = new ConcurrentHashMap<>();

    public Altcha.Challenge createChallenge() {
        if (!captchaSettingsService.enabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "注册人机验证当前未启用");
        }
        try {
            return Altcha.createChallenge(
                    new Altcha.CreateChallengeOptions()
                            .algorithm(CaptchaSettingsService.ALGORITHM)
                            .cost(captchaSettingsService.cost())
                            .expiresInSeconds(captchaSettingsService.expiresInSeconds())
                            .hmacSignatureSecret(captchaSettingsService.requireHmacSecret())
                            .data(Map.of(
                                    "action", ACTION_REGISTER,
                                    "nonce", UUID.randomUUID().toString()
                            ))
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ALTCHA 挑战生成失败");
        }
    }

    public void verifyRegistration(String payload) {
        if (!captchaSettingsService.enabled()) {
            return;
        }
        if (!StringUtils.hasText(payload) || payload.length() > MAX_PAYLOAD_LENGTH) {
            throw invalidChallenge();
        }

        long now = Instant.now().toEpochMilli();
        consumedPayloads.entrySet().removeIf(entry -> entry.getValue() <= now);
        String payloadHash = sha256(payload);
        if (consumedPayloads.containsKey(payloadHash)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "人机验证已使用，请重新验证");
        }

        try {
            Altcha.Payload decoded = Altcha.parsePayload(payload);
            Altcha.VerifySolutionResult result = Altcha.verifySolution(
                    decoded.challenge(),
                    decoded.solution(),
                    captchaSettingsService.requireHmacSecret(),
                    Altcha.kdf(CaptchaSettingsService.ALGORITHM)
            );
            Object action = decoded.challenge().parameters().data().get("action");
            if (!result.verified() || !ACTION_REGISTER.equals(action)) {
                throw invalidChallenge();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidChallenge();
        }

        long consumedUntil = now + (captchaSettingsService.expiresInSeconds() + 60L) * 1000L;
        if (consumedPayloads.putIfAbsent(payloadHash, consumedUntil) != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "人机验证已使用，请重新验证");
        }
    }

    private BusinessException invalidChallenge() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "请重新完成人机验证");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ALTCHA 校验初始化失败");
        }
    }
}
