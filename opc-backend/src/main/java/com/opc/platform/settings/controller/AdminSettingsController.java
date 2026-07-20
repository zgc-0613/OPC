package com.opc.platform.settings.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.settings.dto.MailSettingsUpdateDTO;
import com.opc.platform.settings.dto.MailTestEmailDTO;
import com.opc.platform.settings.service.SettingsService;
import com.opc.platform.settings.vo.MailSettingsVO;
import com.opc.platform.settings.vo.SmtpTestResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/settings/mail")
public class AdminSettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public Result<MailSettingsVO> getMailSettings() {
        return Result.success(settingsService.getMailSettings());
    }

    @PutMapping
    public Result<MailSettingsVO> updateMailSettings(@Valid @RequestBody MailSettingsUpdateDTO dto) {
        return Result.success(settingsService.updateMailSettings(dto));
    }

    @PostMapping("/test-connection")
    public Result<SmtpTestResultVO> testConnection(@Valid @RequestBody MailSettingsUpdateDTO dto) {
        return Result.success(settingsService.testConnection(dto));
    }

    @PostMapping("/test-email")
    public Result<Void> testEmail(@Valid @RequestBody MailTestEmailDTO dto) {
        settingsService.sendTestEmail(dto);
        return Result.success();
    }
}
