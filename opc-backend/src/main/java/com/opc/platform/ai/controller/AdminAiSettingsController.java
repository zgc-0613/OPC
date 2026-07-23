package com.opc.platform.ai.controller;

import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.ai.dto.AiModelDiscoveryRequestDTO;
import com.opc.platform.ai.dto.AiModelOptionDTO;
import com.opc.platform.ai.dto.AiModelSettingsUpdateDTO;
import com.opc.platform.ai.service.AiSettingsService;
import com.opc.platform.ai.vo.AiConnectionTestVO;
import com.opc.platform.ai.vo.AiModelSettingsVO;
import com.opc.platform.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.opc.platform.adminauth.AdminAuthInterceptor.AUTHENTICATED_ADMIN_ATTRIBUTE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/ai-settings")
public class AdminAiSettingsController {

    private final AiSettingsService settingsService;

    @GetMapping
    public Result<AiModelSettingsVO> get() {
        return Result.success(settingsService.get());
    }

    @PutMapping
    public Result<AiModelSettingsVO> update(
            @Valid @RequestBody AiModelSettingsUpdateDTO dto,
            @RequestAttribute(AUTHENTICATED_ADMIN_ATTRIBUTE) AuthenticatedAdmin admin
    ) {
        return Result.success(settingsService.update(dto, admin));
    }

    @PostMapping("/test-connection")
    public Result<AiConnectionTestVO> testConnection(
            @RequestAttribute(AUTHENTICATED_ADMIN_ATTRIBUTE) AuthenticatedAdmin admin
    ) {
        return Result.success(settingsService.testConnection(admin));
    }

    @PostMapping("/models/discover")
    public Result<List<AiModelOptionDTO>> discoverModels(
            @Valid @RequestBody AiModelDiscoveryRequestDTO dto,
            @RequestAttribute(AUTHENTICATED_ADMIN_ATTRIBUTE) AuthenticatedAdmin admin
    ) {
        return Result.success(settingsService.discoverModels(dto, admin));
    }
}
