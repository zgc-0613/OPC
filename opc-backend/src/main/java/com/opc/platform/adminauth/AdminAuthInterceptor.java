package com.opc.platform.adminauth;

import com.opc.platform.adminauth.service.AdminAuthService;
import com.opc.platform.adminauth.entity.AdminAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String AUTHENTICATED_ADMIN_ATTRIBUTE = "opc.authenticatedAdmin";

    private final AdminAuthService adminAuthService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        AdminAccount account = adminAuthService.requireAccount(request.getHeader("X-Admin-Token"));
        request.setAttribute(
                AUTHENTICATED_ADMIN_ATTRIBUTE,
                new AuthenticatedAdmin(account.getId(), account.getUsername())
        );
        return true;
    }
}
