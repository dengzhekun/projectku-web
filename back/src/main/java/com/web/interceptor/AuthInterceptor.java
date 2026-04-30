package com.web.interceptor;

import com.web.exception.BusinessException;
import com.web.security.AuthTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ACCOUNT = new ThreadLocal<>();

    private final AuthTokenService authTokenService;

    public AuthInterceptor(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");

        AuthTokenService.VerifiedToken verifiedToken = authTokenService.verifyRequired(token);
        if (isKnowledgeBaseAdminRequest(request) && !"admin".equals(verifiedToken.account())) {
            throw new BusinessException("FORBIDDEN", "Only administrators can access knowledge base admin APIs");
        }

        CURRENT_USER.set(verifiedToken.userId());
        CURRENT_ACCOUNT.set(verifiedToken.account());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CURRENT_USER.remove();
        CURRENT_ACCOUNT.remove();
    }

    public static Long getCurrentUserId() {
        Long userId = CURRENT_USER.get();
        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", "未获取到用户身份信息");
        }
        return userId;
    }

    public static String getCurrentAccount() {
        String account = CURRENT_ACCOUNT.get();
        if (account == null) {
            throw new BusinessException("UNAUTHORIZED", "未获取到用户账号信息");
        }
        return account;
    }

    private boolean isKnowledgeBaseAdminRequest(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String requestUri = request.getRequestURI();
        return startsWithKbPath(servletPath) || startsWithKbPath(requestUri);
    }

    private boolean startsWithKbPath(String path) {
        return path != null && (path.startsWith("/v1/kb/") || path.startsWith("/api/v1/kb/"));
    }
}
