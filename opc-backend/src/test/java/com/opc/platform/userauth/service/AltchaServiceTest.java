package com.opc.platform.userauth.service;

import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.settings.service.CaptchaSettingsService;
import org.altcha.altcha.v2.Altcha;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AltchaServiceTest {

    private static final String SECRET = "test-altcha-secret-with-at-least-32-characters";

    @Mock
    private CaptchaSettingsService settingsService;

    private AltchaService altchaService;

    @BeforeEach
    void setUp() {
        altchaService = new AltchaService(settingsService);
    }

    @Test
    void verifiesAndConsumesRegistrationPayload() throws Exception {
        enableAltcha();
        String payload = solve(altchaService.createChallenge());

        assertDoesNotThrow(() -> altchaService.verifyRegistration(payload));
        BusinessException replay = assertThrows(
                BusinessException.class,
                () -> altchaService.verifyRegistration(payload)
        );

        assertEquals("人机验证已使用，请重新验证", replay.getMessage());
    }

    @Test
    void rejectsPayloadSignedForAnotherAction() throws Exception {
        when(settingsService.enabled()).thenReturn(true);
        when(settingsService.requireHmacSecret()).thenReturn(SECRET);
        Altcha.Challenge challenge = Altcha.createChallenge(
                new Altcha.CreateChallengeOptions()
                        .algorithm(CaptchaSettingsService.ALGORITHM)
                        .cost(10)
                        .expiresInSeconds(300)
                        .hmacSignatureSecret(SECRET)
                        .data(Map.of("action", "login"))
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> altchaService.verifyRegistration(solve(challenge))
        );

        assertEquals("请重新完成人机验证", exception.getMessage());
    }

    @Test
    void allowsRegistrationWithoutPayloadWhenDisabled() {
        when(settingsService.enabled()).thenReturn(false);

        assertDoesNotThrow(() -> altchaService.verifyRegistration(null));
    }

    private String solve(Altcha.Challenge challenge) throws Exception {
        Altcha.Solution solution = Altcha.solveChallenge(
                challenge,
                Altcha.kdf(CaptchaSettingsService.ALGORITHM)
        );
        JSONObject solutionJson = new JSONObject()
                .put("counter", solution.counter())
                .put("derivedKey", solution.derivedKey());
        if (solution.time() != null) {
            solutionJson.put("time", solution.time());
        }
        JSONObject payload = new JSONObject()
                .put("challenge", new JSONObject(challenge.toJson()))
                .put("solution", solutionJson);
        return Base64.getEncoder().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void enableAltcha() {
        when(settingsService.enabled()).thenReturn(true);
        when(settingsService.cost()).thenReturn(10);
        when(settingsService.expiresInSeconds()).thenReturn(300);
        when(settingsService.requireHmacSecret()).thenReturn(SECRET);
    }
}
