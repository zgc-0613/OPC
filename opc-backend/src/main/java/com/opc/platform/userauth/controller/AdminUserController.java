package com.opc.platform.userauth.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.dto.AdminUserStatusDTO;
import com.opc.platform.userauth.service.AdminUserService;
import com.opc.platform.userauth.vo.AdminUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Result<List<AdminUserVO>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return Result.success(adminUserService.listUsers(keyword, status));
    }

    @PatchMapping("/{id}/status")
    public Result<AdminUserVO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserStatusDTO dto
    ) {
        return Result.success(adminUserService.updateStatus(id, dto.getStatus()));
    }

    @PostMapping("/{id}/revoke-sessions")
    public Result<Void> revokeSessions(@PathVariable Long id) {
        adminUserService.revokeSessions(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        return Result.success();
    }
}
