package com.opc.platform.adminauth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.adminauth.dto.AdminRegistrationRequestDTO;
import com.opc.platform.adminauth.entity.AdminAccount;
import com.opc.platform.adminauth.entity.AdminRegistrationRequest;
import com.opc.platform.adminauth.entity.AdminSession;
import com.opc.platform.adminauth.mapper.AdminAccountMapper;
import com.opc.platform.adminauth.mapper.AdminRegistrationRequestMapper;
import com.opc.platform.adminauth.mapper.AdminSessionMapper;
import com.opc.platform.adminauth.vo.AdminAccountVO;
import com.opc.platform.adminauth.vo.AdminRegistrationRequestVO;
import com.opc.platform.adminauth.vo.AdminLoginVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminAccountMapper adminAccountMapper;
    private final AdminRegistrationRequestMapper requestMapper;
    private final AdminSessionMapper sessionMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${opc.admin.session-hours:12}")
    private int sessionHours;

    public AdminRegistrationRequestVO submitRegistration(AdminRegistrationRequestDTO dto) {
        String username = dto.getUsername().trim();
        AdminAccount existingAccount = adminAccountMapper.selectOne(
                new LambdaQueryWrapper<AdminAccount>()
                        .eq(AdminAccount::getUsername, username)
                        .last("LIMIT 1")
        );
        if (existingAccount != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该管理员用户名已存在");
        }
        AdminRegistrationRequest pendingRequest = requestMapper.selectOne(
                new LambdaQueryWrapper<AdminRegistrationRequest>()
                        .eq(AdminRegistrationRequest::getUsername, username)
                        .eq(AdminRegistrationRequest::getStatus, "pending")
                        .last("LIMIT 1")
        );
        if (pendingRequest != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户名已有待审批申请");
        }

        AdminRegistrationRequest request = new AdminRegistrationRequest();
        request.setUsername(username);
        request.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        request.setStatus("pending");
        try {
            requestMapper.insert(request);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户名已有待审批申请");
        }
        return toRequestVO(request);
    }

    @Transactional
    public AdminRegistrationRequestVO approveRegistration(Long requestId, AdminAccount reviewer) {
        AdminRegistrationRequest request = lockRegistrationRequest(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "管理员注册申请不存在");
        }
        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该管理员注册申请已处理");
        }
        AdminAccount existingAccount = adminAccountMapper.selectOne(
                new LambdaQueryWrapper<AdminAccount>()
                        .eq(AdminAccount::getUsername, request.getUsername())
                        .last("LIMIT 1")
        );
        if (existingAccount != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该管理员用户名已存在");
        }

        AdminAccount account = new AdminAccount();
        account.setUsername(request.getUsername());
        account.setPasswordHash(request.getPasswordHash());
        account.setStatus("active");
        adminAccountMapper.insert(account);

        request.setStatus("approved");
        request.setReviewedBy(reviewer.getId());
        request.setReviewedByUsername(reviewer.getUsername());
        request.setReviewedAt(LocalDateTime.now());
        requestMapper.updateById(request);
        return toRequestVO(request);
    }

    @Transactional
    public AdminRegistrationRequestVO rejectRegistration(Long requestId, AdminAccount reviewer) {
        AdminRegistrationRequest request = lockRegistrationRequest(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "管理员注册申请不存在");
        }
        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该管理员注册申请已处理");
        }
        request.setStatus("rejected");
        request.setReviewedBy(reviewer.getId());
        request.setReviewedByUsername(reviewer.getUsername());
        request.setReviewedAt(LocalDateTime.now());
        requestMapper.updateById(request);
        return toRequestVO(request);
    }

    private AdminRegistrationRequestVO toRequestVO(AdminRegistrationRequest request) {
        AdminRegistrationRequestVO vo = new AdminRegistrationRequestVO();
        vo.setId(request.getId());
        vo.setUsername(request.getUsername());
        vo.setStatus(request.getStatus());
        vo.setReviewedBy(request.getReviewedBy());
        vo.setReviewedByUsername(request.getReviewedByUsername());
        vo.setReviewedAt(request.getReviewedAt());
        vo.setCreatedAt(request.getCreatedAt());
        return vo;
    }

    private AdminRegistrationRequest lockRegistrationRequest(Long requestId) {
        return requestMapper.selectOne(
                new LambdaQueryWrapper<AdminRegistrationRequest>()
                        .eq(AdminRegistrationRequest::getId, requestId)
                        .last("FOR UPDATE")
        );
    }

    public List<AdminRegistrationRequestVO> listRegistrationRequests(String status) {
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toLowerCase() : "pending";
        if (!List.of("pending", "approved", "rejected", "all").contains(normalizedStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "管理员申请状态无效");
        }
        LambdaQueryWrapper<AdminRegistrationRequest> wrapper = new LambdaQueryWrapper<>();
        if (!"all".equals(normalizedStatus)) {
            wrapper.eq(AdminRegistrationRequest::getStatus, normalizedStatus);
        }
        wrapper.orderByDesc(AdminRegistrationRequest::getCreatedAt).last("LIMIT 200");
        return requestMapper.selectList(wrapper).stream().map(this::toRequestVO).toList();
    }

    @Transactional
    public void deleteRegistrationRecord(Long requestId) {
        AdminRegistrationRequest request = requestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "管理员注册申请不存在");
        }
        if ("pending".equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "待审批申请不能作为历史记录删除");
        }
        requestMapper.deleteById(requestId);
    }

    public List<AdminAccountVO> listAccounts() {
        return adminAccountMapper.selectList(
                new LambdaQueryWrapper<AdminAccount>()
                        .orderByAsc(AdminAccount::getCreatedAt)
                        .last("LIMIT 200")
        ).stream().map(this::toAccountVO).toList();
    }

    @Transactional
    public void deleteAccount(Long accountId, String operatorToken) {
        AdminAccount operator = requireAccount(operatorToken);
        if (operator.getId().equals(accountId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能删除当前登录的管理员账号");
        }

        AdminAccount target = adminAccountMapper.selectById(accountId);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "管理员账号不存在");
        }
        if ("active".equals(target.getStatus())) {
            Long activeAccountCount = adminAccountMapper.selectCount(
                    new LambdaQueryWrapper<AdminAccount>()
                            .eq(AdminAccount::getStatus, "active")
            );
            if (activeAccountCount == null || activeAccountCount <= 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不能删除最后一个启用的管理员账号");
            }
        }

        sessionMapper.delete(
                new LambdaQueryWrapper<AdminSession>()
                        .eq(AdminSession::getAdminId, accountId)
        );
        adminAccountMapper.deleteById(accountId);
    }

    private AdminAccountVO toAccountVO(AdminAccount account) {
        AdminAccountVO vo = new AdminAccountVO();
        vo.setId(account.getId());
        vo.setUsername(account.getUsername());
        vo.setStatus(account.getStatus());
        vo.setLastLoginAt(account.getLastLoginAt());
        vo.setCreatedAt(account.getCreatedAt());
        return vo;
    }

    public AdminLoginVO login(String username, String password) {
        AdminAccount account = adminAccountMapper.selectOne(
                new LambdaQueryWrapper<AdminAccount>()
                        .eq(AdminAccount::getUsername, username.trim())
                        .last("LIMIT 1")
        );
        if (account == null || !"active".equals(account.getStatus())
                || !passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "管理员用户名或密码不正确");
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(Math.max(sessionHours, 1));
        String token = UUID.randomUUID().toString().replace("-", "");
        AdminSession session = new AdminSession();
        session.setAdminId(account.getId());
        session.setToken(token);
        session.setExpiresAt(expiresAt);
        sessionMapper.insert(session);
        account.setLastLoginAt(LocalDateTime.now());
        adminAccountMapper.updateById(account);

        AdminLoginVO vo = new AdminLoginVO();
        vo.setUsername(account.getUsername());
        vo.setToken(token);
        vo.setExpiresAt(expiresAt);
        return vo;
    }

    public void requireValid(String token) {
        requireAccount(token);
    }

    public AdminAccount requireAccount(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "管理员登录状态无效或已过期");
        }
        AdminSession session = sessionMapper.selectOne(
                new LambdaQueryWrapper<AdminSession>()
                        .eq(AdminSession::getToken, token)
                        .last("LIMIT 1")
        );
        if (session == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            if (session != null) {
                sessionMapper.deleteById(session.getId());
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "管理员登录状态无效或已过期");
        }
        AdminAccount account = adminAccountMapper.selectById(session.getAdminId());
        if (account == null || !"active".equals(account.getStatus())) {
            sessionMapper.deleteById(session.getId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "管理员登录状态无效或已过期");
        }
        return account;
    }

    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            sessionMapper.delete(
                    new LambdaQueryWrapper<AdminSession>()
                            .eq(AdminSession::getToken, token)
            );
        }
    }
}
