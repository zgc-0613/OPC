package com.opc.platform.userauth;

import com.opc.platform.userauth.service.UserAuthService;
import com.opc.platform.userauth.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class UserAuthInterceptor implements HandlerInterceptor {

    public static final String AUTHENTICATED_USER_ATTRIBUTE = "opc.authenticatedUser";

    private final UserAuthService userAuthService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        UserLoginVO currentUser = userAuthService.getCurrentUser(
                extractBearerToken(request.getHeader("Authorization"))
        );
        request.setAttribute(
                AUTHENTICATED_USER_ATTRIBUTE,
                new AuthenticatedUser(
                        currentUser.getUserId(),
                        currentUser.getUsername(),
                        currentUser.getEmail()
                )
        );
        return true;
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
