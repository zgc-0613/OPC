package com.opc.platform.userauth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.entity.PlatformUser;
import com.opc.platform.userauth.entity.UserSession;
import com.opc.platform.userauth.mapper.PlatformUserMapper;
import com.opc.platform.userauth.mapper.UserSessionMapper;
import com.opc.platform.userauth.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final PlatformUserMapper platformUserMapper;
    private final UserSessionMapper userSessionMapper;

    public List<AdminUserVO> listUsers(String keyword, String status) {
        LambdaQueryWrapper<PlatformUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            wrapper.and(query -> query
                    .like(PlatformUser::getUsername, text)
                    .or()
                    .like(PlatformUser::getEmail, text));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PlatformUser::getStatus, status);
        }
        wrapper.orderByDesc(PlatformUser::getCreatedAt).last("LIMIT 200");
        return platformUserMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Transactional
    public AdminUserVO updateStatus(Long userId, String status) {
        if (!List.of("active", "disabled").contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号状态必须是 active 或 disabled");
        }
        PlatformUser user = requireUser(userId);
        user.setStatus(status);
        platformUserMapper.updateById(user);
        if ("disabled".equals(status)) {
            revokeSessions(userId);
        }
        return toVO(user);
    }

    public void revokeSessions(Long userId) {
        requireUser(userId);
        userSessionMapper.delete(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getUserId, userId)
        );
    }

    @Transactional
    public void deleteUser(Long userId) {
        requireUser(userId);
        userSessionMapper.delete(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getUserId, userId)
        );
        platformUserMapper.deleteById(userId);
    }

    private PlatformUser requireUser(Long userId) {
        PlatformUser user = platformUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        return user;
    }

    private AdminUserVO toVO(PlatformUser user) {
        Long sessionCount = userSessionMapper.selectCount(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getUserId, user.getId())
                        .gt(UserSession::getExpiresAt, LocalDateTime.now())
        );
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setPasswordConfigured(StringUtils.hasText(user.getPasswordHash()));
        vo.setActiveSessionCount(sessionCount);
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
