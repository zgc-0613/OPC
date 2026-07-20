package com.opc.platform.settings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.settings.dto.CaptchaSettingsUpdateDTO;
import com.opc.platform.settings.entity.AppSetting;
import com.opc.platform.settings.mapper.AppSettingMapper;
import com.opc.platform.settings.vo.CaptchaSettingsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CaptchaSettingsService {

    public static final String ALGORITHM = "PBKDF2/SHA-256";

    private static final String KEY_ENABLED = "auth.altcha_enabled";
    private static final String KEY_COST = "auth.altcha_cost";
    private static final String KEY_EXPIRES_SECONDS = "auth.altcha_expires_seconds";
    private static final int DEFAULT_COST = 5000;
    private static final int DEFAULT_EXPIRES_SECONDS = 300;

    private final AppSettingMapper appSettingMapper;

    @Value("${opc.altcha.hmac-secret:}")
    private String hmacSecret;

    public CaptchaSettingsVO getSettings() {
        Map<String, AppSetting> values = loadSettings();
        CaptchaSettingsVO vo = new CaptchaSettingsVO();
        vo.setEnabled(boolValue(values, KEY_ENABLED, false));
        vo.setAlgorithm(ALGORITHM);
        vo.setCost(intValue(values, KEY_COST, DEFAULT_COST));
        vo.setExpiresInSeconds(intValue(values, KEY_EXPIRES_SECONDS, DEFAULT_EXPIRES_SECONDS));
        vo.setSecretConfigured(secretConfigured());
        return vo;
    }

    @Transactional
    public CaptchaSettingsVO updateSettings(CaptchaSettingsUpdateDTO dto) {
        if (Boolean.TRUE.equals(dto.getEnabled()) && !secretConfigured()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "服务器尚未配置 ALTCHA_HMAC_SECRET");
        }
        put(KEY_ENABLED, String.valueOf(dto.getEnabled()));
        put(KEY_COST, String.valueOf(dto.getCost()));
        put(KEY_EXPIRES_SECONDS, String.valueOf(dto.getExpiresInSeconds()));
        return getSettings();
    }

    public boolean enabled() {
        return Boolean.TRUE.equals(getSettings().getEnabled());
    }

    public int cost() {
        return getSettings().getCost();
    }

    public int expiresInSeconds() {
        return getSettings().getExpiresInSeconds();
    }

    public String requireHmacSecret() {
        if (!secretConfigured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ALTCHA 服务器密钥未配置");
        }
        return hmacSecret.trim();
    }

    public boolean secretConfigured() {
        return StringUtils.hasText(hmacSecret) && hmacSecret.trim().length() >= 32;
    }

    private Map<String, AppSetting> loadSettings() {
        List<AppSetting> rows = appSettingMapper.selectList(null);
        Map<String, AppSetting> values = new HashMap<>();
        rows.forEach(row -> values.put(row.getSettingKey(), row));
        return values;
    }

    private void put(String key, String value) {
        AppSetting setting = appSettingMapper.selectOne(
                new LambdaQueryWrapper<AppSetting>()
                        .eq(AppSetting::getSettingKey, key)
                        .last("LIMIT 1")
        );
        if (setting == null) {
            setting = new AppSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setSensitive(false);
            appSettingMapper.insert(setting);
            return;
        }
        setting.setSettingValue(value);
        setting.setSensitive(false);
        appSettingMapper.updateById(setting);
    }

    private int intValue(Map<String, AppSetting> values, String key, int fallback) {
        try {
            AppSetting setting = values.get(key);
            return setting == null ? fallback : Integer.parseInt(setting.getSettingValue());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean boolValue(Map<String, AppSetting> values, String key, boolean fallback) {
        AppSetting setting = values.get(key);
        if (setting == null || setting.getSettingValue() == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(setting.getSettingValue()) || "1".equals(setting.getSettingValue());
    }
}
