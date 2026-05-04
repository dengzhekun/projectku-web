package com.web.service.impl;

import com.web.exception.BusinessException;
import com.web.mapper.PaymentMapper;
import com.web.pojo.Order;
import com.web.pojo.Payment;
import com.web.service.OrderService;
import com.web.service.WalletService;
import com.web.service.payment.AlipayPaymentGateway;
import com.web.service.payment.WechatPaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private OrderService orderService;

    @Mock
    private WalletService walletService;

    @Mock
    private AlipayPaymentGateway alipayPaymentGateway;

    @Mock
    private WechatPaymentGateway wechatPaymentGateway;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void initiatePaymentWithBalanceDebitsWalletAndMarksOrderPaid() {
        Order order = new Order();
        order.setId(9L);
        order.setUserId(3L);
        order.setPayAmount(new BigDecimal("88.50"));
        order.setStatus(0);
        when(orderService.getOrderById(9L)).thenReturn(order);

        Map<String, Object> result = paymentService.initiatePayment(3L, 9L, "balance");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insert(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        assertEquals(9L, payment.getOrderId());
        assertEquals("balance", payment.getChannel());
        assertEquals(new BigDecimal("88.50"), payment.getAmount());
        assertEquals("SUCCESS", payment.getStatus());
        assertNotNull(payment.getPaidAt());

        verify(walletService).payOrder(3L, 9L, new BigDecimal("88.50"), payment.getTradeId());
        verify(orderService).updateOrderStatus(9L, 1);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals("balance", result.get("channel"));
    }

    @Test
    void initiatePaymentWithBalanceRejectsInsufficientBalanceWithoutPaymentRecord() {
        Order order = new Order();
        order.setId(9L);
        order.setUserId(3L);
        order.setPayAmount(new BigDecimal("88.50"));
        order.setStatus(0);
        when(orderService.getOrderById(9L)).thenReturn(order);
        BusinessException insufficient = new BusinessException("INSUFFICIENT_BALANCE", "余额不足");
        org.mockito.Mockito.doThrow(insufficient)
                .when(walletService)
                .payOrder(eq(3L), eq(9L), eq(new BigDecimal("88.50")), org.mockito.Mockito.anyString());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> paymentService.initiatePayment(3L, 9L, "balance"));

        assertEquals("INSUFFICIENT_BALANCE", exception.getCode());
        verify(paymentMapper, never()).insert(any());
        verify(orderService, never()).updateOrderStatus(9L, 1);
    }

    @Test
    void initiateExternalPaymentReusesExistingPendingPaymentForSameChannel() {
        Order order = new Order();
        order.setId(9L);
        order.setUserId(3L);
        order.setPayAmount(new BigDecimal("88.50"));
        order.setStatus(0);
        when(orderService.getOrderById(9L)).thenReturn(order);

        Payment pending = new Payment();
        pending.setOrderId(9L);
        pending.setTradeId("TRD_OLD");
        pending.setChannel("alipay");
        pending.setAmount(new BigDecimal("88.50"));
        pending.setStatus("PENDING");
        when(paymentMapper.getByOrderId(9L)).thenReturn(pending);

        Map<String, Object> result = paymentService.initiatePayment(3L, 9L, "alipay");

        assertEquals("PENDING", result.get("status"));
        assertEquals("TRD_OLD", result.get("tradeId"));
        assertEquals("alipay", result.get("channel"));
        verify(paymentMapper, never()).insert(any());
    }

    @Test
    void initiateAlipayPaymentUsesGatewayPayload() {
        Order order = new Order();
        order.setId(9L);
        order.setOrderNo("ORDER9");
        order.setUserId(3L);
        order.setPayAmount(new BigDecimal("88.50"));
        order.setStatus(0);
        when(orderService.getOrderById(9L)).thenReturn(order);
        when(alipayPaymentGateway.createPagePay(any(Payment.class), eq(order)))
                .thenReturn(Map.of(
                        "mode", "mock",
                        "paymentUrl", "https://openapi-sandbox.dl.alipaydev.com/gateway.do?mock=1",
                        "payForm", "<form></form>"));

        Map<String, Object> result = paymentService.initiatePayment(3L, 9L, "alipay");

        assertEquals("PENDING", result.get("status"));
        assertEquals("alipay", result.get("channel"));
        assertEquals("mock", result.get("mode"));
        assertEquals("<form></form>", result.get("payForm"));
        verify(alipayPaymentGateway).createPagePay(any(Payment.class), eq(order));
    }

    @Test
    void initiateWechatPaymentUsesGatewayPayload() {
        Order order = new Order();
        order.setId(9L);
        order.setOrderNo("ORDER9");
        order.setUserId(3L);
        order.setPayAmount(new BigDecimal("88.50"));
        order.setStatus(0);
        when(orderService.getOrderById(9L)).thenReturn(order);
        when(wechatPaymentGateway.createNativePay(any(Payment.class), eq(order)))
                .thenReturn(Map.of(
                        "mode", "mock",
                        "codeUrl", "weixin://wxpay/mock/TRD1",
                        "qr", "https://mock-payment-gateway.com/wechat/native/TRD1"));

        Map<String, Object> result = paymentService.initiatePayment(3L, 9L, "wechat");

        assertEquals("PENDING", result.get("status"));
        assertEquals("wechat", result.get("channel"));
        assertEquals("mock", result.get("mode"));
        assertEquals("weixin://wxpay/mock/TRD1", result.get("codeUrl"));
        verify(wechatPaymentGateway).createNativePay(any(Payment.class), eq(order));
    }

    @Test
    void failedWebhookKeepsOrderPendingForRetry() {
        Payment payment = new Payment();
        payment.setOrderId(9L);
        payment.setTradeId("TRD123");
        payment.setStatus("PENDING");
        when(paymentMapper.getByTradeId("TRD123")).thenReturn(payment);
        when(paymentMapper.updateStatus(eq("TRD123"), eq("FAILED"), eq(null))).thenReturn(1);

        boolean success = paymentService.handleWebhook("TRD123", "FAILED");

        assertEquals(true, success);
        verify(orderService, never()).updateOrderStatus(9L, 4);
    }

    @Test
    void alipayNotifyRejectsInvalidSignatureWithoutUpdatingPayment() {
        when(alipayPaymentGateway.verifyNotify(Map.of("out_trade_no", "TRD123"))).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> paymentService.handleAlipayNotify(Map.of("out_trade_no", "TRD123")));

        assertEquals("PAYMENT_SIGNATURE_INVALID", exception.getCode());
        verify(paymentMapper, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(orderService);
    }

    @Test
    void alipayNotifyRejectsAmountMismatch() {
        Map<String, String> params = Map.of(
                "out_trade_no", "TRD123",
                "total_amount", "88.49",
                "trade_status", "TRADE_SUCCESS");
        when(alipayPaymentGateway.verifyNotify(params)).thenReturn(true);

        Payment payment = new Payment();
        payment.setOrderId(9L);
        payment.setTradeId("TRD123");
        payment.setChannel("alipay");
        payment.setAmount(new BigDecimal("88.50"));
        payment.setStatus("PENDING");
        when(paymentMapper.getByTradeId("TRD123")).thenReturn(payment);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> paymentService.handleAlipayNotify(params));

        assertEquals("PAYMENT_AMOUNT_MISMATCH", exception.getCode());
        verify(paymentMapper, never()).updateStatus(any(), any(), any());
        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void alipayNotifyMarksPendingPaymentSuccessWhenVerifiedAndAmountMatches() {
        Map<String, String> params = Map.of(
                "out_trade_no", "TRD123",
                "total_amount", "88.50",
                "trade_status", "TRADE_SUCCESS");
        when(alipayPaymentGateway.verifyNotify(params)).thenReturn(true);

        Payment payment = new Payment();
        payment.setOrderId(9L);
        payment.setTradeId("TRD123");
        payment.setChannel("alipay");
        payment.setAmount(new BigDecimal("88.50"));
        payment.setStatus("PENDING");
        when(paymentMapper.getByTradeId("TRD123")).thenReturn(payment);
        when(paymentMapper.updateStatus(eq("TRD123"), eq("SUCCESS"), any())).thenReturn(1);

        boolean result = paymentService.handleAlipayNotify(params);

        assertEquals(true, result);
        verify(orderService).updateOrderStatus(9L, 1);
    }

    @Test
    void alipayNotifyIsIdempotentForAlreadyProcessedPayment() {
        Map<String, String> params = Map.of(
                "out_trade_no", "TRD123",
                "total_amount", "88.50",
                "trade_status", "TRADE_SUCCESS");
        when(alipayPaymentGateway.verifyNotify(params)).thenReturn(true);

        Payment payment = new Payment();
        payment.setOrderId(9L);
        payment.setTradeId("TRD123");
        payment.setChannel("alipay");
        payment.setAmount(new BigDecimal("88.50"));
        payment.setStatus("SUCCESS");
        when(paymentMapper.getByTradeId("TRD123")).thenReturn(payment);

        boolean result = paymentService.handleAlipayNotify(params);

        assertEquals(true, result);
        verify(paymentMapper, never()).updateStatus(any(), any(), any());
        verify(orderService, never()).updateOrderStatus(any(), any());
    }
}
