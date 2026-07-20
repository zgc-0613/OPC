package com.opc.platform.settings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.settings.dto.MailSettingsUpdateDTO;
import com.opc.platform.settings.dto.MailTestEmailDTO;
import com.opc.platform.settings.entity.AppSetting;
import com.opc.platform.settings.mapper.AppSettingMapper;
import com.opc.platform.settings.vo.MailSettingsVO;
import com.opc.platform.settings.vo.SmtpTestResultVO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String KEY_SITE_NAME = "site.name";
    private static final String KEY_MAIL_ENABLED = "auth.mail_enabled";
    private static final String KEY_CODE_MINUTES = "auth.verification_code_minutes";
    private static final String KEY_RESEND_SECONDS = "auth.resend_interval_seconds";
    private static final String KEY_SESSION_DAYS = "auth.session_days";
    private static final String KEY_SMTP_HOST = "smtp.host";
    private static final String KEY_SMTP_PORT = "smtp.port";
    private static final String KEY_SMTP_USERNAME = "smtp.username";
    private static final String KEY_SMTP_PASSWORD = "smtp.password";
    private static final String KEY_SMTP_FROM_EMAIL = "smtp.from_email";
    private static final String KEY_SMTP_FROM_NAME = "smtp.from_name";
    private static final String KEY_SMTP_SECURITY = "smtp.security_mode";
    private static final String KEY_SMTP_TIMEOUT = "smtp.timeout_seconds";
    private static final String KEY_MAIL_SUBJECT = "mail.verification_subject";
    private static final String KEY_MAIL_HTML = "mail.verification_html";

    private static final String DEFAULT_SUBJECT = "[{{site_name}}] 邮箱验证码";
    private static final String DEFAULT_HTML_MARKER = "__SOLOFIRM_DEFAULT__";
    private static final String LEGACY_DEFAULT_HTML_PREFIX = "<div style=\"font-family:serif;color:#181a18\"><h2>{{site_name}}</h2>";
    private static final ClassPathResource SOLOFIRM_LOGO = new ClassPathResource("mail/solofirm-logo.png");
    private static final String DEFAULT_HTML = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>SoloFirm 邮箱验证码</title>
            </head>
            <body style="margin:0;padding:24px;background:#eef0eb;color:#181a18;font-family:Georgia,Times New Roman,serif;">
              <div style="display:none;max-height:0;overflow:hidden;opacity:0;">你的 SoloFirm 注册验证码是 {{code}}</div>
              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="width:100%;background:#eef0eb;border-collapse:collapse;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="width:100%;max-width:640px;overflow:hidden;border:1px solid #cfd3ce;border-radius:8px;background:#fbfbf8;border-collapse:separate;">
                      <tr>
                        <td style="padding:26px 30px;border-bottom:1px solid #d7dad5;">
                          <table role="presentation" cellspacing="0" cellpadding="0" style="border-collapse:collapse;">
                            <tr>
                              <td width="64" style="width:64px;vertical-align:middle;"><img src="cid:solofirm-logo" width="52" height="52" alt="SoloFirm" style="display:block;width:52px;height:52px;border:0;border-radius:14px;"></td>
                              <td style="vertical-align:middle;">
                                <div style="color:#181a18;font-family:Bookman Old Style,Georgia,serif;font-size:23px;font-weight:700;line-height:1.1;">SoloFirm</div>
                                <div style="margin-top:5px;color:#646a65;font-size:12px;line-height:1.2;">{{site_name}} / ACCOUNT VERIFICATION</div>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:34px 30px 30px;">
                          <div style="color:#555c56;font-size:12px;font-weight:700;line-height:1.4;">邮箱安全验证</div>
                          <h1 style="margin:9px 0 14px;color:#181a18;font-size:30px;font-weight:500;line-height:1.2;">验证你的邮箱</h1>
                          <p style="margin:0 0 22px;color:#4f5650;font-size:15px;line-height:1.75;">你正在登录 {{site_name}}。请使用下面的验证码完成验证：</p>
                          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="width:100%;border-radius:6px;background:#181a18;border-collapse:separate;">
                            <tr>
                              <td align="center" style="padding:24px 18px;">
                                <div style="margin-bottom:9px;color:#aeb4ae;font-size:11px;line-height:1.2;">VERIFICATION CODE</div>
                                <div style="color:#fbfbf8;font-family:Bookman Old Style,Georgia,serif;font-size:36px;font-weight:700;line-height:1;letter-spacing:7px;">{{code}}</div>
                              </td>
                            </tr>
                          </table>
                          <p style="margin:22px 0 0;color:#343a35;font-size:14px;line-height:1.7;">验证码将在 <strong>{{expires_minutes}} 分钟</strong>后失效，请勿转发给他人。</p>
                          <p style="margin:8px 0 0;color:#737a74;font-size:13px;line-height:1.7;">如果不是你本人操作，可以忽略这封邮件。</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:18px 30px;border-top:1px solid #d7dad5;background:#f1f2ee;color:#747b75;font-size:12px;line-height:1.6;">此邮件由 SoloFirm 自动发送，请勿直接回复。</td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;

    private final AppSettingMapper appSettingMapper;

    @Value("${opc.settings.encryption-secret:local-development-only}")
    private String encryptionSecret;

    @Value("${opc.settings.encryption-salt:9f4c2d8a7b6e5f10}")
    private String encryptionSalt;

    public MailSettingsVO getMailSettings() {
        Map<String, AppSetting> values = loadSettings();
        MailSettingsVO vo = new MailSettingsVO();
        vo.setMailEnabled(boolValue(values, KEY_MAIL_ENABLED, false));
        vo.setSiteName(value(values, KEY_SITE_NAME, "SoloFirm"));
        vo.setHost(value(values, KEY_SMTP_HOST, "smtp.qq.com"));
        vo.setPort(intValue(values, KEY_SMTP_PORT, 465));
        vo.setUsername(value(values, KEY_SMTP_USERNAME, ""));
        vo.setPasswordConfigured(hasStoredPassword(values));
        vo.setFromEmail(value(values, KEY_SMTP_FROM_EMAIL, ""));
        vo.setFromName(value(values, KEY_SMTP_FROM_NAME, "SoloFirm"));
        vo.setSecurityMode(normalizeSecurity(value(values, KEY_SMTP_SECURITY, "ssl")));
        vo.setTimeoutSeconds(intValue(values, KEY_SMTP_TIMEOUT, 12));
        vo.setVerificationCodeMinutes(intValue(values, KEY_CODE_MINUTES, 10));
        vo.setResendIntervalSeconds(intValue(values, KEY_RESEND_SECONDS, 60));
        vo.setSessionDays(intValue(values, KEY_SESSION_DAYS, 30));
        vo.setVerificationSubject(value(values, KEY_MAIL_SUBJECT, DEFAULT_SUBJECT));
        String configuredHtml = value(values, KEY_MAIL_HTML, DEFAULT_HTML);
        boolean usesOfficialTemplate = DEFAULT_HTML_MARKER.equals(configuredHtml)
                || configuredHtml.startsWith(LEGACY_DEFAULT_HTML_PREFIX);
        vo.setVerificationHtml(usesOfficialTemplate ? DEFAULT_HTML : configuredHtml);
        return vo;
    }

    @Transactional
    public MailSettingsVO updateMailSettings(MailSettingsUpdateDTO dto) {
        ResolvedMailSettings resolved = resolve(dto);
        if (resolved.mailEnabled()) {
            validateComplete(resolved);
        }

        put(KEY_MAIL_ENABLED, String.valueOf(resolved.mailEnabled()), false);
        put(KEY_SITE_NAME, resolved.siteName(), false);
        put(KEY_SMTP_HOST, resolved.host(), false);
        put(KEY_SMTP_PORT, String.valueOf(resolved.port()), false);
        put(KEY_SMTP_USERNAME, resolved.username(), false);
        put(KEY_SMTP_FROM_EMAIL, resolved.fromEmail(), false);
        put(KEY_SMTP_FROM_NAME, resolved.fromName(), false);
        put(KEY_SMTP_SECURITY, resolved.securityMode(), false);
        put(KEY_SMTP_TIMEOUT, String.valueOf(resolved.timeoutSeconds()), false);
        put(KEY_CODE_MINUTES, String.valueOf(resolved.verificationCodeMinutes()), false);
        put(KEY_RESEND_SECONDS, String.valueOf(resolved.resendIntervalSeconds()), false);
        put(KEY_SESSION_DAYS, String.valueOf(resolved.sessionDays()), false);
        put(KEY_MAIL_SUBJECT, resolved.verificationSubject(), false);
        put(KEY_MAIL_HTML, resolved.verificationHtml(), false);

        if (Boolean.TRUE.equals(dto.getClearPassword())) {
            delete(KEY_SMTP_PASSWORD);
        } else if (StringUtils.hasText(dto.getPassword())) {
            put(KEY_SMTP_PASSWORD, dto.getPassword(), true);
        }
        return getMailSettings();
    }

    public SmtpTestResultVO testConnection(MailSettingsUpdateDTO dto) {
        ResolvedMailSettings settings = resolve(dto);
        validateComplete(settings);
        try {
            buildSender(settings).testConnection();
            return new SmtpTestResultVO(settings.host(), settings.port(), "SMTP 连接成功");
        } catch (MessagingException | MailException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "SMTP 连接失败：" + safeMessage(exception));
        }
    }

    public void sendTestEmail(MailTestEmailDTO dto) {
        ResolvedMailSettings settings = resolve(dto);
        validateComplete(settings);
        String subject = render(settings.verificationSubject(), settings, "123456");
        String html = render(settings.verificationHtml(), settings, "123456");
        sendHtml(settings, dto.getRecipient(), "[测试] " + subject, html);
    }

    public void sendVerificationEmail(String recipient, String code) {
        ResolvedMailSettings settings = resolve(null);
        if (!settings.mailEnabled()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邮件发送尚未启用");
        }
        validateComplete(settings);
        sendHtml(
                settings,
                recipient,
                render(settings.verificationSubject(), settings, code),
                render(settings.verificationHtml(), settings, code)
        );
    }

    public int verificationCodeMinutes() {
        return getMailSettings().getVerificationCodeMinutes();
    }

    public int resendIntervalSeconds() {
        return getMailSettings().getResendIntervalSeconds();
    }

    public int sessionDays() {
        return getMailSettings().getSessionDays();
    }

    public boolean mailEnabled() {
        return Boolean.TRUE.equals(getMailSettings().getMailEnabled());
    }

    private void sendHtml(ResolvedMailSettings settings, String recipient, String subject, String html) {
        try {
            JavaMailSenderImpl sender = buildSender(settings);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.fromEmail(), settings.fromName());
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.addInline("solofirm-logo", SOLOFIRM_LOGO, "image/png");
            sender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮件发送失败：" + safeMessage(exception));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮件发送失败：" + safeMessage(exception));
        }
    }

    private JavaMailSenderImpl buildSender(ResolvedMailSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        sender.setPort(settings.port());
        sender.setUsername(settings.username());
        sender.setPassword(settings.password());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        int timeout = Math.max(settings.timeoutSeconds(), 1) * 1000;
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(StringUtils.hasText(settings.username())));
        properties.put("mail.smtp.connectiontimeout", String.valueOf(timeout));
        properties.put("mail.smtp.timeout", String.valueOf(timeout));
        properties.put("mail.smtp.writetimeout", String.valueOf(timeout));
        properties.put("mail.smtp.ssl.enable", String.valueOf("ssl".equals(settings.securityMode())));
        properties.put("mail.smtp.starttls.enable", String.valueOf("starttls".equals(settings.securityMode())));
        properties.put("mail.smtp.starttls.required", String.valueOf("starttls".equals(settings.securityMode())));
        return sender;
    }

    private ResolvedMailSettings resolve(MailSettingsUpdateDTO dto) {
        Map<String, AppSetting> values = loadSettings();
        String storedPassword = readSensitive(values.get(KEY_SMTP_PASSWORD));
        boolean clearPassword = dto != null && Boolean.TRUE.equals(dto.getClearPassword());
        String password = clearPassword
                ? ""
                : firstText(dto == null ? null : dto.getPassword(), storedPassword);

        return new ResolvedMailSettings(
                first(dto == null ? null : dto.getMailEnabled(), boolValue(values, KEY_MAIL_ENABLED, false)),
                firstText(dto == null ? null : dto.getSiteName(), value(values, KEY_SITE_NAME, "SoloFirm")),
                firstText(dto == null ? null : dto.getHost(), value(values, KEY_SMTP_HOST, "smtp.qq.com")),
                first(dto == null ? null : dto.getPort(), intValue(values, KEY_SMTP_PORT, 465)),
                firstText(dto == null ? null : dto.getUsername(), value(values, KEY_SMTP_USERNAME, "")),
                password,
                firstText(dto == null ? null : dto.getFromEmail(), value(values, KEY_SMTP_FROM_EMAIL, "")),
                firstText(dto == null ? null : dto.getFromName(), value(values, KEY_SMTP_FROM_NAME, "SoloFirm")),
                normalizeSecurity(firstText(dto == null ? null : dto.getSecurityMode(), value(values, KEY_SMTP_SECURITY, "ssl"))),
                first(dto == null ? null : dto.getTimeoutSeconds(), intValue(values, KEY_SMTP_TIMEOUT, 12)),
                first(dto == null ? null : dto.getVerificationCodeMinutes(), intValue(values, KEY_CODE_MINUTES, 10)),
                first(dto == null ? null : dto.getResendIntervalSeconds(), intValue(values, KEY_RESEND_SECONDS, 60)),
                first(dto == null ? null : dto.getSessionDays(), intValue(values, KEY_SESSION_DAYS, 30)),
                firstText(dto == null ? null : dto.getVerificationSubject(), value(values, KEY_MAIL_SUBJECT, DEFAULT_SUBJECT)),
                firstText(dto == null ? null : dto.getVerificationHtml(), value(values, KEY_MAIL_HTML, DEFAULT_HTML))
        );
    }

    private void validateComplete(ResolvedMailSettings settings) {
        if (!StringUtils.hasText(settings.host())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写 SMTP 主机");
        }
        if (!StringUtils.hasText(settings.username())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写 SMTP 用户名");
        }
        if (!StringUtils.hasText(settings.password())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写 SMTP 密码");
        }
        if (!StringUtils.hasText(settings.fromEmail())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写发件人邮箱");
        }
    }

    private String render(String template, ResolvedMailSettings settings, String code) {
        return template
                .replace("{{site_name}}", settings.siteName())
                .replace("{{code}}", code)
                .replace("{{expires_minutes}}", String.valueOf(settings.verificationCodeMinutes()));
    }

    private Map<String, AppSetting> loadSettings() {
        List<AppSetting> rows = appSettingMapper.selectList(null);
        Map<String, AppSetting> values = new HashMap<>();
        rows.forEach(row -> values.put(row.getSettingKey(), row));
        return values;
    }

    private void put(String key, String rawValue, boolean sensitive) {
        AppSetting existing = appSettingMapper.selectOne(
                new LambdaQueryWrapper<AppSetting>()
                        .eq(AppSetting::getSettingKey, key)
                        .last("LIMIT 1")
        );
        String storedValue = sensitive ? encryptor().encrypt(rawValue) : rawValue;
        if (existing == null) {
            AppSetting setting = new AppSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(storedValue);
            setting.setSensitive(sensitive);
            appSettingMapper.insert(setting);
            return;
        }
        existing.setSettingValue(storedValue);
        existing.setSensitive(sensitive);
        appSettingMapper.updateById(existing);
    }

    private void delete(String key) {
        appSettingMapper.delete(new LambdaQueryWrapper<AppSetting>().eq(AppSetting::getSettingKey, key));
    }

    private boolean hasStoredPassword(Map<String, AppSetting> values) {
        AppSetting setting = values.get(KEY_SMTP_PASSWORD);
        return setting != null && StringUtils.hasText(setting.getSettingValue());
    }

    private String readSensitive(AppSetting setting) {
        if (setting == null || !StringUtils.hasText(setting.getSettingValue())) {
            return "";
        }
        try {
            return Boolean.TRUE.equals(setting.getSensitive())
                    ? encryptor().decrypt(setting.getSettingValue())
                    : setting.getSettingValue();
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "敏感设置无法解密，请检查 OPC_SETTINGS_SECRET");
        }
    }

    private TextEncryptor encryptor() {
        return Encryptors.text(encryptionSecret, encryptionSalt);
    }

    private String value(Map<String, AppSetting> values, String key, String fallback) {
        AppSetting setting = values.get(key);
        return setting == null || setting.getSettingValue() == null ? fallback : setting.getSettingValue();
    }

    private int intValue(Map<String, AppSetting> values, String key, int fallback) {
        try {
            return Integer.parseInt(value(values, key, String.valueOf(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean boolValue(Map<String, AppSetting> values, String key, boolean fallback) {
        String raw = value(values, key, String.valueOf(fallback));
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private String normalizeSecurity(String mode) {
        String normalized = firstText(mode, "ssl").toLowerCase(Locale.ROOT);
        if (!List.of("ssl", "starttls", "plain").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "SMTP 安全模式必须是 ssl、starttls 或 plain");
        }
        return normalized;
    }

    private String firstText(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private <T> T first(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private String safeMessage(Exception exception) {
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private record ResolvedMailSettings(
            boolean mailEnabled,
            String siteName,
            String host,
            int port,
            String username,
            String password,
            String fromEmail,
            String fromName,
            String securityMode,
            int timeoutSeconds,
            int verificationCodeMinutes,
            int resendIntervalSeconds,
            int sessionDays,
            String verificationSubject,
            String verificationHtml
    ) {
    }
}
