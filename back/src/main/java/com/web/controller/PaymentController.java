package com.web.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import com.web.exception.BusinessException;
import com.web.interceptor.AuthInterceptor;
import com.web.pojo.Payment;
import com.web.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 支付接口
 */
@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    /**
     * 发起支付
     * POST /v1/payments/{orderId}/pay
     */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<Map<String, Object>> initiatePayment(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> params) {
            
        Long userId = AuthInterceptor.getCurrentUserId();
        String channel = params.getOrDefault("channel", "alipay").toString();
        
        Map<String, Object> payParams = paymentService.initiatePayment(userId, orderId, channel);
        
        Map<String, Object> meta = MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("requestId", UUID.randomUUID().toString())
                .build();
                
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", payParams)
                .put("meta", meta)
                .build());
    }

    /**
     * 查询支付状态
     * GET /v1/payments/{orderId}/status
     */
    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable Long orderId) {
        Long userId = AuthInterceptor.getCurrentUserId();
        Payment payment = paymentService.getPaymentStatus(orderId, userId);
        
        if (payment == null) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "支付记录不存在");
        }
        
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", BeanUtil.beanToMap(payment, false, true))
                .build());
    }

    /**
     * 模拟支付回调 (通常由第三方支付网关调用，不需要 Token)
     * POST /v1/payments/webhook
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> params) {
        String tradeId = params.containsKey("tradeId") && params.get("tradeId") != null ? params.get("tradeId").toString() : null;
        String status = params.containsKey("status") && params.get("status") != null ? params.get("status").toString() : null;
        if (tradeId == null || tradeId.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "tradeId 不能为空");
        }
        if (status == null || status.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "status 不能为空");
        }
        String normalized = status.trim().toUpperCase();
        if (!"SUCCESS".equals(normalized) && !"FAILED".equals(normalized)) {
            throw new BusinessException("VALIDATION_FAILED", "status 参数非法");
        }
        
        // 实际开发中需要验签(sign)，此处略过
        boolean success = paymentService.handleWebhook(tradeId, normalized);
        
        return ResponseEntity.ok(MapUtil.builder(new java.util.HashMap<String, Object>())
                .put("code", success ? 200 : 500)
                .put("message", success ? "success" : "failed")
                .build());
    }

    @PostMapping(value = "/alipay/notify", consumes = "application/x-www-form-urlencoded")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        boolean success = paymentService.handleAlipayNotify(params);
        return success ? "success" : "failure";
    }
}
