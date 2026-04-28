package com.web.exception;

import cn.hutool.core.map.MapUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;
import java.util.UUID;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        Map<String, Object> error = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", e.getCode())
                .put("message", e.getMessage())
                .build();

        Map<String, Object> meta = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("requestId", UUID.randomUUID().toString())
                .build();

        Map<String, Object> result = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("error", error)
                .put("meta", meta)
                .build();

        HttpStatus status = mapBusinessStatus(e.getCode());
        return ResponseEntity.status(status).body(result);
    }

    private HttpStatus mapBusinessStatus(String code) {
        if ("UNAUTHORIZED".equals(code)) return HttpStatus.UNAUTHORIZED;
        if ("FORBIDDEN".equals(code)) return HttpStatus.FORBIDDEN;
        if ("NOT_FOUND".equals(code)) return HttpStatus.NOT_FOUND;
        if ("ORDER_NOT_FOUND".equals(code)) return HttpStatus.NOT_FOUND;
        if ("PAYMENT_NOT_FOUND".equals(code)) return HttpStatus.NOT_FOUND;
        if ("PRODUCT_NOT_FOUND".equals(code)) return HttpStatus.NOT_FOUND;
        if ("USER_NOT_FOUND".equals(code)) return HttpStatus.NOT_FOUND;
        if ("AI_SERVICE_UNAVAILABLE".equals(code)) return HttpStatus.SERVICE_UNAVAILABLE;
        if ("STOCK_NOT_ENOUGH".equals(code)) return HttpStatus.CONFLICT;
        if ("ORDER_STATE_INVALID".equals(code)) return HttpStatus.CONFLICT;
        if ("PAYMENT_FAILED".equals(code)) return HttpStatus.CONFLICT;
        return HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        Map<String, Object> error = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", "INTERNAL_ERROR")
                .put("message", "Internal server error")
                .build();

        Map<String, Object> meta = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("requestId", UUID.randomUUID().toString())
                .build();

        Map<String, Object> result = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("error", error)
                .put("meta", meta)
                .build();

        return ResponseEntity.status(500).body(result);
    }
}
