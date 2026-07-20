package com.opc.platform.adminauth.controller;

import com.opc.platform.adminauth.dto.AdminLoginDTO;
import com.opc.platform.adminauth.dto.AdminRegistrationRequestDTO;
import com.opc.platform.adminauth.service.AdminAuthService;
import com.opc.platform.adminauth.vo.AdminLoginVO;
import com.opc.platform.adminauth.vo.AdminRegistrationRequestVO;
import com.opc.platform.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO dto) {
        return Result.success(adminAuthService.login(dto.getUsername(), dto.getPassword()));
    }

    @PostMapping("/register-request")
    public Result<AdminRegistrationRequestVO> registerRequest(
            @Valid @RequestBody AdminRegistrationRequestDTO dto
    ) {
        return Result.success(adminAuthService.submitRegistration(dto));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminAuthService.logout(token);
        return Result.success();
    }

    @GetMapping("/session")
    public Result<Void> session() {
        return Result.success();
    }
}
