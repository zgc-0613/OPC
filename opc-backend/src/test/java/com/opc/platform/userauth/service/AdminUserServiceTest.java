package com.opc.platform.userauth.service;

import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.entity.PlatformUser;
import com.opc.platform.userauth.mapper.PlatformUserMapper;
import com.opc.platform.userauth.mapper.UserSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private PlatformUserMapper platformUserMapper;

    @Mock
    private UserSessionMapper userSessionMapper;

    @Test
    void administratorCanDeleteUserAndTheirSessions() {
        PlatformUser user = new PlatformUser();
        user.setId(17L);
        when(platformUserMapper.selectById(17L)).thenReturn(user);
        AdminUserService service = new AdminUserService(platformUserMapper, userSessionMapper);

        service.deleteUser(17L);

        InOrder deletionOrder = inOrder(userSessionMapper, platformUserMapper);
        deletionOrder.verify(userSessionMapper).delete(any());
        deletionOrder.verify(platformUserMapper).deleteById(17L);
    }

    @Test
    void deletingMissingUserReturnsNotFoundWithoutChangingSessions() {
        when(platformUserMapper.selectById(99L)).thenReturn(null);
        AdminUserService service = new AdminUserService(platformUserMapper, userSessionMapper);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.deleteUser(99L)
        );

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        verify(userSessionMapper, never()).delete(any());
        verify(platformUserMapper, never()).deleteById(99L);
    }
}
