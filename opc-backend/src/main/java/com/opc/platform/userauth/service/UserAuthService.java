package com.opc.platform.userauth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.dto.SendEmailCodeDTO;
import com.opc.platform.userauth.dto.VerifyEmailLoginDTO;
import com.opc.platform.userauth.entity.EmailVerificationCode;
import com.opc.platform.userauth.entity.PlatformUser;
import com.opc.platform.userauth.entity.UserSession;
import com.opc.platform.userauth.mapper.EmailVerificationCodeMapper;
import com.opc.platform.userauth.mapper.PlatformUserMapper;
import com.opc.platform.userauth.mapper.UserSessionMapper;
import com.opc.platform.userauth.vo.SendEmailCodeVO;
import com.opc.platform.userauth.vo.UserLoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAuthService {

    private static final String PURPOSE_USER_LOGIN = "user_login";
    private static final String STATUS_ACTIVE = "active";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformUserMapper platformUserMapper;
    private final EmailVerificationCodeMapper emailVerificationCodeMapper;
    private final UserSessionMapper userSessionMapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${opc.auth.mail-enabled:false}")
    private boolean mailEnabled;

    @Value("${opc.auth.verification-code-minutes:10}")
    private int verificationCodeMinutes;

    @Value("${opc.auth.resend-interval-seconds:60}")
    private int resendIntervalSeconds;

    @Value("${opc.auth.session-days:30}")
    private int sessionDays;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Transactional
    public SendEmailCodeVO sendEmailCode(SendEmailCodeDTO dto) {
        String email = normalizeEmail(dto.getEmail());
        checkSendFrequency(email);
        String code = generateCode();

        EmailVerificationCode verificationCode = new EmailVerificationCode();
        verificationCode.setEmail(email);
        verificationCode.setCode(code);
        verificationCode.setPurpose(PURPOSE_USER_LOGIN);
        verificationCode.setUsed(false);
        verificationCode.setExpiresAt(LocalDateTime.now().plusMinutes(verificationCodeMinutes));
        emailVerificationCodeMapper.insert(verificationCode);

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

    @Transactional
    public UserLoginVO verifyEmailCodeAndLogin(VerifyEmailLoginDTO dto) {
        String email = normalizeEmail(dto.getEmail());
        String username = dto.getUsername().trim();
        EmailVerificationCode verificationCode = findLatestCode(email);

        if (verificationCode == null || Boolean.TRUE.equals(verificationCode.getUsed())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码不存在或已使用");
        }
        if (verificationCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (!verificationCode.getCode().equals(dto.getCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码不正确");
        }

        PlatformUser user = findUserByEmail(email);
        LocalDateTime now = LocalDateTime.now();
        if (user == null) {
            user = new PlatformUser();
            user.setEmail(email);
            user.setUsername(username);
            user.setStatus(STATUS_ACTIVE);
            user.setLastLoginAt(now);
            platformUserMapper.insert(user);
        } else {
            if (!STATUS_ACTIVE.equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "该账号已被禁用");
            }
            user.setUsername(username);
            user.setLastLoginAt(now);
            platformUserMapper.updateById(user);
        }

        verificationCode.setUsed(true);
        verificationCode.setUsedAt(now);
        emailVerificationCodeMapper.updateById(verificationCode);

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setToken(UUID.randomUUID().toString().replace("-", ""));
        session.setExpiresAt(now.plusDays(sessionDays));
        userSessionMapper.insert(session);

        return toLoginVO(user, session);
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
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邮箱服务未启用");
        }
        if (!StringUtils.hasText(mailFrom)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邮箱服务未配置发件账号");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("SoloFirm 登录验证码");
        message.setText("你的 SoloFirm 登录验证码是：" + code + "，" + verificationCodeMinutes + " 分钟内有效。");
        mailSender.send(message);
    }

    private void checkSendFrequency(String email) {
        EmailVerificationCode latestCode = findLatestCode(email);
        if (latestCode == null || latestCode.getCreatedAt() == null) {
            return;
        }
        LocalDateTime nextAllowedAt = latestCode.getCreatedAt().plusSeconds(resendIntervalSeconds);
        if (nextAllowedAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码发送过于频繁，请稍后再试");
        }
    }

    private EmailVerificationCode findLatestCode(String email) {
        return emailVerificationCodeMapper.selectOne(
                new LambdaQueryWrapper<EmailVerificationCode>()
                        .eq(EmailVerificationCode::getEmail, email)
                        .eq(EmailVerificationCode::getPurpose, PURPOSE_USER_LOGIN)
                        .orderByDesc(EmailVerificationCode::getId)
                        .last("LIMIT 1")
        );
    }

    private PlatformUser findUserByEmail(String email) {
        return platformUserMapper.selectOne(
                new LambdaQueryWrapper<PlatformUser>()
                        .eq(PlatformUser::getEmail, email)
                        .last("LIMIT 1")
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
