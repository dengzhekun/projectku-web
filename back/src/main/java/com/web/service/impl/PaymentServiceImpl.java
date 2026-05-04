package com.web.service.impl;

import cn.hutool.core.util.IdUtil;
import com.web.exception.BusinessException;
import com.web.mapper.PaymentMapper;
import com.web.pojo.Order;
import com.web.pojo.Payment;
import com.web.service.OrderService;
import com.web.service.PaymentService;
import com.web.service.WalletService;
import com.web.service.payment.AlipayPaymentGateway;
import com.web.service.payment.WechatPaymentGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentMapper paymentMapper;
    
    @Autowired
    private OrderService orderService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private AlipayPaymentGateway alipayPaymentGateway;

    @Autowired
    private WechatPaymentGateway wechatPaymentGateway;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> initiatePayment(Long userId, Long orderId, String channel) {
        if (channel == null || channel.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "支付渠道不能为空");
        }
        String normalized = channel.trim().toLowerCase();
        if (!"alipay".equals(normalized) && !"wechat".equals(normalized)
                && !"unionpay".equals(normalized) && !"balance".equals(normalized)) {
            throw new BusinessException("VALIDATION_FAILED", "不支持的支付渠道");
        }
        Order order = orderService.getOrderById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (order.getStatus() != 0) { // 0: 待支付
            throw new BusinessException("ORDER_STATE_INVALID", "订单状态不允许支付");
        }
        
        // 检查是否已有待支付记录，防止重复创建 (简单的幂等处理)
        Payment existing = paymentMapper.getByOrderId(orderId);
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            throw new BusinessException("PAYMENT_FAILED", "订单已支付，请勿重复操作");
        }
        
        String tradeId = "TRD" + IdUtil.getSnowflakeNextIdStr();

        if ("balance".equals(normalized)) {
            walletService.payOrder(userId, orderId, order.getPayAmount(), tradeId);
            paymentMapper.updatePendingStatusByOrderId(orderId, "FAILED", null);

            Payment payment = new Payment();
            payment.setOrderId(orderId);
            payment.setTradeId(tradeId);
            payment.setChannel(normalized);
            payment.setAmount(order.getPayAmount());
            payment.setStatus("SUCCESS");
            payment.setPaidAt(LocalDateTime.now());
            paymentMapper.insert(payment);
            orderService.updateOrderStatus(orderId, 1);

            Map<String, Object> result = new HashMap<>();
            result.put("tradeId", tradeId);
            result.put("channel", normalized);
            result.put("status", "SUCCESS");
            result.put("paidAt", payment.getPaidAt());
            return result;
        }

        if (existing != null && "PENDING".equals(existing.getStatus())) {
            if (normalized.equals(existing.getChannel())) {
                return pendingPaymentResult(existing);
            }
            paymentMapper.updateStatus(existing.getTradeId(), "FAILED", null);
        }

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setTradeId(tradeId);
        payment.setChannel(normalized);
        payment.setAmount(order.getPayAmount());
        payment.setStatus("PENDING");
        
        paymentMapper.insert(payment);
        
        // 模拟返回给前端的支付网关参数
        Map<String, Object> result = new HashMap<>();
        result.put("tradeId", tradeId);
        result.put("channel", normalized);
        result.put("status", "PENDING");
        if ("alipay".equals(normalized)) {
            result.putAll(alipayPaymentGateway.createPagePay(payment, order));
        } else if ("wechat".equals(normalized)) {
            result.putAll(wechatPaymentGateway.createNativePay(payment, order));
        } else {
            result.put("mode", "mock");
            result.put("qr", "https://mock-payment-gateway.com/qr/" + tradeId);
        }
        result.put("expiresAt", LocalDateTime.now().plusMinutes(30));
        
        return result;
    }

    @Override
    public Payment getPaymentStatus(Long orderId, Long userId) {
        Order order = orderService.getOrderById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        return paymentMapper.getByOrderId(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleWebhook(String tradeId, String status) {
        Payment payment = paymentMapper.getByTradeId(tradeId);
        if (payment == null) {
            return false;
        }
        // 幂等校验，如果已经处理过，直接返回成功
        if (!"PENDING".equals(payment.getStatus())) {
            return true;
        }
        
        LocalDateTime paidAt = "SUCCESS".equals(status) ? LocalDateTime.now() : null;
        int rows = paymentMapper.updateStatus(tradeId, status, paidAt);
        
        // 支付成功，联动更新订单状态为已支付 (1: 已支付)
        if (rows > 0 && "SUCCESS".equals(status)) {
            orderService.updateOrderStatus(payment.getOrderId(), 1);
        }
        
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleAlipayNotify(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            throw new BusinessException("VALIDATION_FAILED", "支付宝回调参数不能为空");
        }
        if (!alipayPaymentGateway.verifyNotify(params)) {
            throw new BusinessException("PAYMENT_SIGNATURE_INVALID", "支付宝回调验签失败");
        }

        String tradeId = trimToNull(params.get("out_trade_no"));
        String tradeStatus = trimToNull(params.get("trade_status"));
        String totalAmount = trimToNull(params.get("total_amount"));
        if (tradeId == null || tradeStatus == null || totalAmount == null) {
            throw new BusinessException("VALIDATION_FAILED", "支付宝回调参数不完整");
        }

        String normalizedStatus = normalizeAlipayTradeStatus(tradeStatus);
        if (normalizedStatus == null) {
            return true;
        }

        Payment payment = paymentMapper.getByTradeId(tradeId);
        if (payment == null) {
            return false;
        }
        if (!"alipay".equals(payment.getChannel())) {
            throw new BusinessException("PAYMENT_CHANNEL_MISMATCH", "支付渠道不匹配");
        }
        if (payment.getAmount() == null || payment.getAmount().compareTo(new BigDecimal(totalAmount)) != 0) {
            throw new BusinessException("PAYMENT_AMOUNT_MISMATCH", "支付金额不匹配");
        }
        if (!"PENDING".equals(payment.getStatus())) {
            return true;
        }

        LocalDateTime paidAt = "SUCCESS".equals(normalizedStatus) ? LocalDateTime.now() : null;
        int rows = paymentMapper.updateStatus(tradeId, normalizedStatus, paidAt);
        if (rows > 0 && "SUCCESS".equals(normalizedStatus)) {
            orderService.updateOrderStatus(payment.getOrderId(), 1);
        }
        return rows > 0;
    }

    private Map<String, Object> pendingPaymentResult(Payment payment) {
        Map<String, Object> result = new HashMap<>();
        result.put("tradeId", payment.getTradeId());
        result.put("channel", payment.getChannel());
        result.put("status", "PENDING");
        result.put("qr", "https://mock-payment-gateway.com/qr/" + payment.getTradeId());
        result.put("expiresAt", LocalDateTime.now().plusMinutes(30));
        return result;
    }

    private String normalizeAlipayTradeStatus(String tradeStatus) {
        String normalized = tradeStatus.trim().toUpperCase();
        if ("TRADE_SUCCESS".equals(normalized) || "TRADE_FINISHED".equals(normalized)) {
            return "SUCCESS";
        }
        if ("TRADE_CLOSED".equals(normalized)) {
            return "FAILED";
        }
        if ("WAIT_BUYER_PAY".equals(normalized)) {
            return null;
        }
        throw new BusinessException("VALIDATION_FAILED", "支付宝交易状态非法");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
