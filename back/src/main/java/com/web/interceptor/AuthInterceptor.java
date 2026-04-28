package com.web.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.web.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final byte[] JWT_KEY = "projectku_secret_key".getBytes();
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ACCOUNT = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");

        if (StrUtil.isBlank(token) || !token.startsWith("Bearer ")) {
            throw new BusinessException("UNAUTHORIZED", "未登录或令牌失效");
        }

        token = token.substring(7);

        try {
            boolean verify = JWTUtil.verify(token, JWT_KEY);
            if (!verify) {
                throw new BusinessException("UNAUTHORIZED", "令牌签名无效");
            }

            JWT jwt = JWTUtil.parseToken(token);
            Long exp = Long.valueOf(jwt.getPayload("exp").toString());
            if (System.currentTimeMillis() > exp) {
                throw new BusinessException("UNAUTHORIZED", "令牌已过期");
            }

            Long userId = Long.valueOf(jwt.getPayload("id").toString());
            String account = String.valueOf(jwt.getPayload("account"));
            if (isKnowledgeBaseAdminRequest(request) && !"admin".equals(account)) {
                throw new BusinessException("FORBIDDEN", "Only administrators can access knowledge base admin APIs");
            }

            CURRENT_USER.set(userId);
            CURRENT_ACCOUNT.set(account);
            return true;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("UNAUTHORIZED", "解析令牌失败");
        }
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
