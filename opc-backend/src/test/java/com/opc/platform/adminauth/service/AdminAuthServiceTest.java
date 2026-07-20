package com.opc.platform.adminauth.service;

import com.opc.platform.adminauth.dto.AdminRegistrationRequestDTO;
import com.opc.platform.adminauth.entity.AdminAccount;
import com.opc.platform.adminauth.entity.AdminRegistrationRequest;
import com.opc.platform.adminauth.entity.AdminSession;
import com.opc.platform.adminauth.mapper.AdminAccountMapper;
import com.opc.platform.adminauth.mapper.AdminRegistrationRequestMapper;
import com.opc.platform.adminauth.mapper.AdminSessionMapper;
import com.opc.platform.adminauth.vo.AdminRegistrationRequestVO;
import com.opc.platform.adminauth.vo.AdminLoginVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private AdminAccountMapper adminAccountMapper;

    @Mock
    private AdminRegistrationRequestMapper requestMapper;

    @Mock
    private AdminSessionMapper sessionMapper;

    private PasswordEncoder passwordEncoder;
    private AdminAuthService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new AdminAuthService(adminAccountMapper, requestMapper, sessionMapper, passwordEncoder);
        ReflectionTestUtils.setField(service, "sessionHours", 12);
    }

    @Test
    void registrationRequestStoresHashedPasswordAndStartsPending() {
        when(adminAccountMapper.selectOne(any())).thenReturn(null);
        when(requestMapper.selectOne(any())).thenReturn(null);
        AdminRegistrationRequestDTO dto = new AdminRegistrationRequestDTO();
        dto.setUsername("reviewer2");
        dto.setPassword("secure-password");

        AdminRegistrationRequestVO result = service.submitRegistration(dto);

        ArgumentCaptor<AdminRegistrationRequest> captor = ArgumentCaptor.forClass(AdminRegistrationRequest.class);
        verify(requestMapper).insert(captor.capture());
        AdminRegistrationRequest stored = captor.getValue();
        assertNotEquals("secure-password", stored.getPasswordHash());
        assertTrue(passwordEncoder.matches("secure-password", stored.getPasswordHash()));
        assertEquals("pending", result.getStatus());
        assertEquals("reviewer2", result.getUsername());
    }

    @Test
    void concurrentDuplicatePendingRegistrationReturnsAUsefulBusinessError() {
        when(adminAccountMapper.selectOne(any())).thenReturn(null);
        when(requestMapper.selectOne(any())).thenReturn(null);
        when(requestMapper.insert(any(AdminRegistrationRequest.class)))
                .thenThrow(new DuplicateKeyException("duplicate pending username"));
        AdminRegistrationRequestDTO dto = new AdminRegistrationRequestDTO();
        dto.setUsername("reviewer2");
        dto.setPassword("secure-password");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.submitRegistration(dto)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("该用户名已有待审批申请", exception.getMessage());
    }

    @Test
    void existingAdministratorCanApprovePendingRequest() {
        AdminRegistrationRequest request = new AdminRegistrationRequest();
        request.setId(21L);
        request.setUsername("reviewer2");
        request.setPasswordHash(passwordEncoder.encode("secure-password"));
        request.setStatus("pending");
        when(requestMapper.selectOne(any())).thenReturn(request);
        when(adminAccountMapper.selectOne(any())).thenReturn(null);

        AdminRegistrationRequestVO result = service.approveRegistration(21L, activeAccount(7L, "ACha_"));

        ArgumentCaptor<AdminAccount> accountCaptor = ArgumentCaptor.forClass(AdminAccount.class);
        verify(adminAccountMapper).insert(accountCaptor.capture());
        assertEquals("reviewer2", accountCaptor.getValue().getUsername());
        assertEquals("active", accountCaptor.getValue().getStatus());
        assertEquals("approved", result.getStatus());
        assertEquals(7L, result.getReviewedBy());
        assertEquals("ACha_", result.getReviewedByUsername());
    }

    @Test
    void approvedAdministratorCanLoginAndGetsPersistentSession() {
        AdminAccount account = new AdminAccount();
        account.setId(7L);
        account.setUsername("ACha_");
        account.setPasswordHash(passwordEncoder.encode("initial-password"));
        account.setStatus("active");
        when(adminAccountMapper.selectOne(any())).thenReturn(account);

        AdminLoginVO result = service.login("ACha_", "initial-password");

        ArgumentCaptor<AdminSession> sessionCaptor = ArgumentCaptor.forClass(AdminSession.class);
        verify(sessionMapper).insert(sessionCaptor.capture());
        assertEquals(7L, sessionCaptor.getValue().getAdminId());
        assertEquals("ACha_", result.getUsername());
        assertTrue(result.getToken() != null && !result.getToken().isBlank());
    }

    @Test
    void persistentSessionResolvesActiveAdministrator() {
        AdminSession session = new AdminSession();
        session.setAdminId(7L);
        session.setToken("valid-token");
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        AdminAccount account = new AdminAccount();
        account.setId(7L);
        account.setUsername("ACha_");
        account.setStatus("active");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(adminAccountMapper.selectById(7L)).thenReturn(account);

        AdminAccount result = service.requireAccount("valid-token");

        assertEquals(7L, result.getId());
        assertEquals("ACha_", result.getUsername());
    }

    @Test
    void existingAdministratorCanRejectPendingRequestWithoutCreatingAccount() {
        AdminRegistrationRequest request = new AdminRegistrationRequest();
        request.setId(22L);
        request.setUsername("reviewer3");
        request.setPasswordHash(passwordEncoder.encode("secure-password"));
        request.setStatus("pending");
        when(requestMapper.selectOne(any())).thenReturn(request);

        AdminRegistrationRequestVO result = service.rejectRegistration(22L, activeAccount(7L, "ACha_"));

        verify(adminAccountMapper, never()).insert(any(AdminAccount.class));
        verify(requestMapper).updateById(request);
        assertEquals("rejected", result.getStatus());
        assertEquals(7L, result.getReviewedBy());
        assertEquals("ACha_", result.getReviewedByUsername());
    }

    @Test
    void administratorCanDeleteAReviewedRegistrationRecord() {
        AdminRegistrationRequest request = new AdminRegistrationRequest();
        request.setId(23L);
        request.setUsername("reviewer4");
        request.setStatus("approved");
        when(requestMapper.selectById(23L)).thenReturn(request);

        service.deleteRegistrationRecord(23L);

        verify(requestMapper).deleteById(23L);
    }

    @Test
    void pendingRegistrationCannotBeDeletedAsHistory() {
        AdminRegistrationRequest request = new AdminRegistrationRequest();
        request.setId(24L);
        request.setUsername("reviewer5");
        request.setStatus("pending");
        when(requestMapper.selectById(24L)).thenReturn(request);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.deleteRegistrationRecord(24L)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(requestMapper, never()).deleteById(24L);
    }

    @Test
    void administratorCanDeleteAnotherAccountAndItsSessions() {
        AdminAccount operator = activeAccount(7L, "ACha_");
        AdminAccount target = activeAccount(8L, "reviewer2");
        when(sessionMapper.selectOne(any())).thenReturn(validSession(7L, "operator-token"));
        when(adminAccountMapper.selectById(7L)).thenReturn(operator);
        when(adminAccountMapper.selectById(8L)).thenReturn(target);
        when(adminAccountMapper.selectCount(any())).thenReturn(2L);

        service.deleteAccount(8L, "operator-token");

        InOrder deletionOrder = inOrder(sessionMapper, adminAccountMapper);
        deletionOrder.verify(sessionMapper).delete(any());
        deletionOrder.verify(adminAccountMapper).deleteById(8L);
    }

    @Test
    void administratorCannotDeleteOwnAccount() {
        AdminAccount operator = activeAccount(7L, "ACha_");
        when(sessionMapper.selectOne(any())).thenReturn(validSession(7L, "operator-token"));
        when(adminAccountMapper.selectById(7L)).thenReturn(operator);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.deleteAccount(7L, "operator-token")
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(sessionMapper, never()).delete(any());
        verify(adminAccountMapper, never()).deleteById(7L);
    }

    @Test
    void lastActiveAdministratorCannotBeDeleted() {
        AdminAccount operator = activeAccount(7L, "ACha_");
        AdminAccount target = activeAccount(8L, "reviewer2");
        when(sessionMapper.selectOne(any())).thenReturn(validSession(7L, "operator-token"));
        when(adminAccountMapper.selectById(7L)).thenReturn(operator);
        when(adminAccountMapper.selectById(8L)).thenReturn(target);
        when(adminAccountMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.deleteAccount(8L, "operator-token")
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(sessionMapper, never()).delete(any());
        verify(adminAccountMapper, never()).deleteById(8L);
    }

    private AdminAccount activeAccount(Long id, String username) {
        AdminAccount account = new AdminAccount();
        account.setId(id);
        account.setUsername(username);
        account.setStatus("active");
        return account;
    }

    private AdminSession validSession(Long adminId, String token) {
        AdminSession session = new AdminSession();
        session.setId(41L);
        session.setAdminId(adminId);
        session.setToken(token);
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        return session;
    }
}
