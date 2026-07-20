package com.opc.platform.settings.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.settings.dto.CaptchaSettingsUpdateDTO;
import com.opc.platform.settings.service.CaptchaSettingsService;
import com.opc.platform.settings.vo.CaptchaSettingsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/settings/captcha")
public class AdminCaptchaSettingsController {

    private final CaptchaSettingsService captchaSettingsService;

    @GetMapping
    public Result<CaptchaSettingsVO> getSettings() {
        return Result.success(captchaSettingsService.getSettings());
    }

    @PutMapping
    public Result<CaptchaSettingsVO> updateSettings(@Valid @RequestBody CaptchaSettingsUpdateDTO dto) {
        return Result.success(captchaSettingsService.updateSettings(dto));
    }
}
