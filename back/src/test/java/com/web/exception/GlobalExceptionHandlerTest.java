package com.web.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unauthorizedBusinessExceptionUsesHttp401() {
        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(
                new BusinessException("UNAUTHORIZED", "token invalid"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void forbiddenBusinessExceptionUsesHttp403() {
        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(
                new BusinessException("FORBIDDEN", "admin only"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void notFoundBusinessExceptionUsesHttp404() {
        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(
                new BusinessException("ORDER_NOT_FOUND", "missing"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void conflictBusinessExceptionUsesHttp409() {
        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(
                new BusinessException("STOCK_NOT_ENOUGH", "no stock"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void aiServiceUnavailableUsesHttp503() {
        ResponseEntity<Map<String, Object>> response = handler.handleBusinessException(
                new BusinessException("AI_SERVICE_UNAVAILABLE", "ai offline"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void unexpectedExceptionDoesNotExposeRawExceptionMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleException(
                new RuntimeException("database password leaked"));

        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        String message = String.valueOf(error.get("message"));
        assertFalse(message.contains("database password leaked"));
        assertTrue(message.length() > 0);
    }
}
