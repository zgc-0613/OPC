package com.opc.platform.userauth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.settings.service.SettingsService;
import com.opc.platform.userauth.dto.PasswordLoginDTO;
import com.opc.platform.userauth.dto.RegisterUserDTO;
import com.opc.platform.userauth.dto.SendEmailCodeDTO;
import com.opc.platform.userauth.entity.EmailVerificationCode;
import com.opc.platform.userauth.entity.PlatformUser;
import com.opc.platform.userauth.entity.UserSession;
import com.opc.platform.userauth.mapper.EmailVerificationCodeMapper;
import com.opc.platform.userauth.mapper.PlatformUserMapper;
import com.opc.platform.userauth.mapper.UserSessionMapper;
import com.opc.platform.userauth.vo.SendEmailCodeVO;
import com.opc.platform.userauth.vo.UserLoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAuthService {

    private static final String PURPOSE_USER_REGISTER = "user_register";
    private static final String STATUS_ACTIVE = "active";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformUserMapper platformUserMapper;
    private final EmailVerificationCodeMapper emailVerificationCodeMapper;
    private final UserSessionMapper userSessionMapper;
    private final SettingsService settingsService;
    private final AltchaService altchaService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserLoginVO loginWithPassword(PasswordLoginDTO dto) {
        PlatformUser user = findUserByIdentifier(dto.getIdentifier());
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码不正确");
        }
        if (!StringUtils.hasText(user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该账号尚未设置密码，请使用注册入口完成账号升级");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码不正确");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginAt(now);
        platformUserMapper.updateById(user);
        return toLoginVO(user, createSession(user.getId(), now));
    }

    @Transactional
    public UserLoginVO registerWithEmailCode(RegisterUserDTO dto) {
        String email = normalizeEmail(dto.getEmail());
        String username = requireUsername(dto.getUsername());
        EmailVerificationCode verificationCode = requireValidRegistrationCode(email, dto.getCode());
        PlatformUser user = findUserByEmail(email);
        PlatformUser usernameOwner = findUserByUsername(username);

        if (usernameOwner != null && (user == null || !usernameOwner.getId().equals(user.getId()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户名已被使用");
        }
        if (user != null && StringUtils.hasText(user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该邮箱已注册，请直接登录");
        }
        if (user != null && !STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该账号已被禁用");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean upgradingLegacyAccount = user != null;
        if (!upgradingLegacyAccount) {
            user = new PlatformUser();
            user.setEmail(email);
            user.setStatus(STATUS_ACTIVE);
        }
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setLastLoginAt(now);

        if (upgradingLegacyAccount) {
            platformUserMapper.updateById(user);
            revokeSessions(user.getId());
        } else {
            platformUserMapper.insert(user);
        }

        verificationCode.setUsed(true);
        verificationCode.setUsedAt(now);
        emailVerificationCodeMapper.updateById(verificationCode);
        return toLoginVO(user, createSession(user.getId(), now));
    }

    @Transactional
    public SendEmailCodeVO sendEmailCode(SendEmailCodeDTO dto) {
        String email = normalizeEmail(dto.getEmail());
        altchaService.verifyRegistration(dto.getAltcha());
        PlatformUser existingUser = findUserByEmail(email);
        if (existingUser != null && StringUtils.hasText(existingUser.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该邮箱已注册，请直接登录");
        }
        if (existingUser != null && !STATUS_ACTIVE.equals(existingUser.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该账号已被禁用");
        }
        checkSendFrequency(email);
        String code = generateCode();
        int verificationCodeMinutes = settingsService.verificationCodeMinutes();

        EmailVerificationCode verificationCode = new EmailVerificationCode();
        verificationCode.setEmail(email);
        verificationCode.setCode(code);
        verificationCode.setPurpose(PURPOSE_USER_REGISTER);
        verificationCode.setUsed(false);
        verificationCode.setExpiresAt(LocalDateTime.now().plusMinutes(verificationCodeMinutes));
        emailVerificationCodeMapper.insert(verificationCode);

        boolean mailEnabled = settingsService.mailEnabled();
        if (mailEnabled) {
            sendMail(email, code);
        }

        SendEmailCodeVO vo = new SendEmailCodeVO();
        vo.setEmail(email);
        vo.setExpiresInMinutes(verificationCodeMinutes);
        if (!mailEnabled) {
            vo.setDevCode(code);
        }
        return vo;
    }

    public UserLoginVO getCurrentUser(String token) {
        UserSession session = findValidSession(token);
        PlatformUser user = platformUserMapper.selectById(session.getUserId());
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效");
        }
        return toLoginVO(user, session);
    }

    public void logout(String token) {
        userSessionMapper.delete(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getToken, token)
        );
    }

    private void sendMail(String email, String code) {
        settingsService.sendVerificationEmail(email, code);
    }

    private void checkSendFrequency(String email) {
        EmailVerificationCode latestCode = findLatestCode(email);
        if (latestCode == null || latestCode.getCreatedAt() == null) {
            return;
        }
        LocalDateTime nextAllowedAt = latestCode.getCreatedAt().plusSeconds(settingsService.resendIntervalSeconds());
        if (nextAllowedAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码发送过于频繁，请稍后再试");
        }
    }

    private EmailVerificationCode findLatestCode(String email) {
        return emailVerificationCodeMapper.selectOne(
                new LambdaQueryWrapper<EmailVerificationCode>()
                        .eq(EmailVerificationCode::getEmail, email)
                        .eq(EmailVerificationCode::getPurpose, PURPOSE_USER_REGISTER)
                        .orderByDesc(EmailVerificationCode::getId)
                        .last("LIMIT 1")
        );
    }

    private EmailVerificationCode requireValidRegistrationCode(String email, String submittedCode) {
        EmailVerificationCode verificationCode = emailVerificationCodeMapper.selectOne(
                new LambdaQueryWrapper<EmailVerificationCode>()
                        .eq(EmailVerificationCode::getEmail, email)
                        .eq(EmailVerificationCode::getPurpose, PURPOSE_USER_REGISTER)
                        .orderByDesc(EmailVerificationCode::getId)
                        .last("LIMIT 1")
        );
        if (verificationCode == null || Boolean.TRUE.equals(verificationCode.getUsed())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码不存在或已使用");
        }
        if (verificationCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (!verificationCode.getCode().equals(submittedCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码不正确");
        }
        return verificationCode;
    }

    private PlatformUser findUserByEmail(String email) {
        return platformUserMapper.selectOne(
                new LambdaQueryWrapper<PlatformUser>()
                        .eq(PlatformUser::getEmail, email)
                        .last("LIMIT 1")
        );
    }

    private PlatformUser findUserByUsername(String username) {
        return platformUserMapper.selectOne(
                new LambdaQueryWrapper<PlatformUser>()
                        .eq(PlatformUser::getUsername, username)
                        .last("LIMIT 1")
        );
    }

    private PlatformUser findUserByIdentifier(String identifier) {
        String normalized = identifier.trim();
        String normalizedEmail = normalized.toLowerCase();
        return platformUserMapper.selectOne(
                new LambdaQueryWrapper<PlatformUser>()
                        .and(query -> query
                                .eq(PlatformUser::getEmail, normalizedEmail)
                                .or()
                                .eq(PlatformUser::getUsername, normalized))
                        .last("LIMIT 1")
        );
    }

    private UserSession createSession(Long userId, LocalDateTime now) {
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setToken(UUID.randomUUID().toString().replace("-", ""));
        session.setExpiresAt(now.plusDays(settingsService.sessionDays()));
        userSessionMapper.insert(session);
        return session;
    }

    private void revokeSessions(Long userId) {
        userSessionMapper.delete(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getUserId, userId)
        );
    }

    private UserSession findValidSession(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        UserSession session = userSessionMapper.selectOne(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getToken, token)
                        .last("LIMIT 1")
        );
        if (session == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已过期");
        }
        return session;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String requireUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "注册时必须填写用户名");
        }
        String normalized = username.trim();
        if (normalized.length() < 2 || normalized.length() > 30) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名长度必须为 2-30 个字符");
        }
        return normalized;
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private UserLoginVO toLoginVO(PlatformUser user, UserSession session) {
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setToken(session.getToken());
        vo.setExpiresAt(session.getExpiresAt());
        return vo;
    }
}
