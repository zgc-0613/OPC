package com.opc.platform.adminauth.controller;

import com.opc.platform.adminauth.entity.AdminAccount;
import com.opc.platform.adminauth.service.AdminAuthService;
import com.opc.platform.adminauth.vo.AdminAccountVO;
import com.opc.platform.adminauth.vo.AdminRegistrationRequestVO;
import com.opc.platform.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminRegistrationController {

    private final AdminAuthService adminAuthService;

    @GetMapping("/registration-requests")
    public Result<List<AdminRegistrationRequestVO>> listRequests(
            @RequestParam(defaultValue = "pending") String status
    ) {
        return Result.success(adminAuthService.listRegistrationRequests(status));
    }

    @PostMapping("/registration-requests/{requestId}/approve")
    public Result<AdminRegistrationRequestVO> approve(
            @PathVariable Long requestId,
            @RequestHeader("X-Admin-Token") String token
    ) {
        AdminAccount reviewer = adminAuthService.requireAccount(token);
        return Result.success(adminAuthService.approveRegistration(requestId, reviewer));
    }

    @PostMapping("/registration-requests/{requestId}/reject")
    public Result<AdminRegistrationRequestVO> reject(
            @PathVariable Long requestId,
            @RequestHeader("X-Admin-Token") String token
    ) {
        AdminAccount reviewer = adminAuthService.requireAccount(token);
        return Result.success(adminAuthService.rejectRegistration(requestId, reviewer));
    }

    @DeleteMapping("/registration-requests/{requestId}")
    public Result<Void> deleteRegistrationRecord(@PathVariable Long requestId) {
        adminAuthService.deleteRegistrationRecord(requestId);
        return Result.success();
    }

    @GetMapping("/accounts")
    public Result<List<AdminAccountVO>> listAccounts() {
        return Result.success(adminAuthService.listAccounts());
    }

    @DeleteMapping("/accounts/{accountId}")
    public Result<Void> deleteAccount(
            @PathVariable Long accountId,
            @RequestHeader("X-Admin-Token") String token
    ) {
        adminAuthService.deleteAccount(accountId, token);
        return Result.success();
    }
}
