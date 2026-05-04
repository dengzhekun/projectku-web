package com.web.interceptor;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.web.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthInterceptorTest {

    private static final byte[] JWT_KEY = "projectku_secret_key".getBytes();

    private final AuthInterceptor interceptor = new AuthInterceptor(new com.web.security.AuthTokenService());
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanUp() throws Exception {
        interceptor.afterCompletion(new MockHttpServletRequest(), response, null, null);
    }

    @Test
    void nonAdminUserCannotAccessKnowledgeBaseAdminApi() {
        MockHttpServletRequest request = kbRequestWithToken(tokenFor(12L, "user@example.com"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interceptor.preHandle(request, response, null));

        assertEquals("FORBIDDEN", exception.getCode());
    }

    @Test
    void adminUserCanAccessKnowledgeBaseAdminApi() {
        MockHttpServletRequest request = kbRequestWithToken(tokenFor(1L, "admin"));

        assertDoesNotThrow(() -> interceptor.preHandle(request, response, null));
        assertEquals(1L, AuthInterceptor.getCurrentUserId());
    }

    @Test
    void nonAdminUserCannotAccessAdminApi() {
        MockHttpServletRequest request = adminRequestWithToken(tokenFor(12L, "user@example.com"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> interceptor.preHandle(request, response, null));

        assertEquals("FORBIDDEN", exception.getCode());
    }

    @Test
    void adminUserCanAccessAdminApi() {
        MockHttpServletRequest request = adminRequestWithToken(tokenFor(1L, "admin"));

        assertDoesNotThrow(() -> interceptor.preHandle(request, response, null));
        assertEquals(1L, AuthInterceptor.getCurrentUserId());
    }

    private MockHttpServletRequest kbRequestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/kb/documents");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private MockHttpServletRequest adminRequestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/admin/payments/overview");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private String tokenFor(Long userId, String account) {
        return JWT.create()
                .setPayload("id", userId)
                .setPayload("account", account)
                .setPayload("exp", System.currentTimeMillis() + 7200 * 1000)
                .setSigner(JWTSignerUtil.hs256(JWT_KEY))
                .sign();
    }
}
