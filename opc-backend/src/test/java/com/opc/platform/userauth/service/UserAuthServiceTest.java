package com.opc.platform.userauth.service;

import com.opc.platform.settings.service.SettingsService;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.dto.PasswordLoginDTO;
import com.opc.platform.userauth.dto.RegisterUserDTO;
import com.opc.platform.userauth.dto.SendEmailCodeDTO;
import com.opc.platform.userauth.entity.EmailVerificationCode;
import com.opc.platform.userauth.entity.PlatformUser;
import com.opc.platform.userauth.mapper.EmailVerificationCodeMapper;
import com.opc.platform.userauth.mapper.PlatformUserMapper;
import com.opc.platform.userauth.mapper.UserSessionMapper;
import com.opc.platform.userauth.vo.UserLoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceTest {

    @Mock
    private PlatformUserMapper platformUserMapper;

    @Mock
    private EmailVerificationCodeMapper emailVerificationCodeMapper;

    @Mock
    private UserSessionMapper userSessionMapper;

    @Mock
    private SettingsService settingsService;

    @Mock
    private AltchaService altchaService;

    private PasswordEncoder passwordEncoder;
    private UserAuthService userAuthService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        userAuthService = new UserAuthService(
                platformUserMapper,
                emailVerificationCodeMapper,
                userSessionMapper,
                settingsService,
                altchaService,
                passwordEncoder
        );
    }

    @Test
    void userCanLoginWithEmailAndPassword() {
        PlatformUser user = activeUser();
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        when(platformUserMapper.selectOne(any())).thenReturn(user);
        when(settingsService.sessionDays()).thenReturn(30);

        PasswordLoginDTO dto = new PasswordLoginDTO();
        dto.setIdentifier("owner@example.com");
        dto.setPassword("correct-password");

        UserLoginVO result = userAuthService.loginWithPassword(dto);

        assertEquals(7L, result.getUserId());
        assertEquals("owner", result.getUsername());
        assertEquals("owner@example.com", result.getEmail());
    }

    @Test
    void userCanRegisterWithEmailCodeAndHashedPassword() {
        EmailVerificationCode verificationCode = new EmailVerificationCode();
        verificationCode.setId(11L);
        verificationCode.setEmail("new@example.com");
        verificationCode.setCode("123456");
        verificationCode.setPurpose("user_register");
        verificationCode.setUsed(false);
        verificationCode.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(emailVerificationCodeMapper.selectOne(any())).thenReturn(verificationCode);
        when(platformUserMapper.selectOne(any())).thenReturn(null);
        when(settingsService.sessionDays()).thenReturn(30);

        RegisterUserDTO dto = new RegisterUserDTO();
        dto.setUsername("new-owner");
        dto.setEmail("new@example.com");
        dto.setPassword("correct-password");
        dto.setCode("123456");

        UserLoginVO result = userAuthService.registerWithEmailCode(dto);

        ArgumentCaptor<PlatformUser> userCaptor = ArgumentCaptor.forClass(PlatformUser.class);
        verify(platformUserMapper).insert(userCaptor.capture());
        PlatformUser storedUser = userCaptor.getValue();
        assertNotEquals("correct-password", storedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches("correct-password", storedUser.getPasswordHash()));
        assertEquals("new@example.com", result.getEmail());
    }

    @Test
    void emailCodeIsRegistrationOnlyAndRequiresAltcha() {
        when(platformUserMapper.selectOne(any())).thenReturn(null);
        when(emailVerificationCodeMapper.selectOne(any())).thenReturn(null);
        when(settingsService.verificationCodeMinutes()).thenReturn(10);
        when(settingsService.mailEnabled()).thenReturn(false);

        SendEmailCodeDTO dto = new SendEmailCodeDTO();
        dto.setEmail("new@example.com");
        dto.setAltcha("proof-payload");

        userAuthService.sendEmailCode(dto);

        verify(altchaService).verifyRegistration("proof-payload");
        ArgumentCaptor<EmailVerificationCode> codeCaptor = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(emailVerificationCodeMapper).insert(codeCaptor.capture());
        assertEquals("user_register", codeCaptor.getValue().getPurpose());
    }

    @Test
    void wrongPasswordUsesGenericLoginError() {
        PlatformUser user = activeUser();
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        when(platformUserMapper.selectOne(any())).thenReturn(user);

        PasswordLoginDTO dto = new PasswordLoginDTO();
        dto.setIdentifier("owner");
        dto.setPassword("wrong-password");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userAuthService.loginWithPassword(dto)
        );

        assertEquals("账号或密码不正确", exception.getMessage());
    }

    @Test
    void legacyEmailCodeAccountCanSetPasswordWithoutLosingIdentity() {
        PlatformUser legacyUser = activeUser();
        EmailVerificationCode verificationCode = new EmailVerificationCode();
        verificationCode.setEmail("owner@example.com");
        verificationCode.setCode("123456");
        verificationCode.setPurpose("user_register");
        verificationCode.setUsed(false);
        verificationCode.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(emailVerificationCodeMapper.selectOne(any())).thenReturn(verificationCode);
        when(platformUserMapper.selectOne(any())).thenReturn(legacyUser).thenReturn(null);
        when(settingsService.sessionDays()).thenReturn(30);

        RegisterUserDTO dto = new RegisterUserDTO();
        dto.setUsername("owner");
        dto.setEmail("owner@example.com");
        dto.setPassword("upgraded-password");
        dto.setCode("123456");

        UserLoginVO result = userAuthService.registerWithEmailCode(dto);

        verify(platformUserMapper).updateById(legacyUser);
        verify(userSessionMapper).delete(any());
        assertTrue(passwordEncoder.matches("upgraded-password", legacyUser.getPasswordHash()));
        assertEquals(7L, result.getUserId());
    }

    private PlatformUser activeUser() {
        PlatformUser user = new PlatformUser();
        user.setId(7L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        user.setStatus("active");
        return user;
    }
}
