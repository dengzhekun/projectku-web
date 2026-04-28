package com.web.service.impl;

import com.web.exception.BusinessException;
import com.web.mapper.PaymentMapper;
import com.web.pojo.Order;
import com.web.pojo.Payment;
import com.web.service.OrderService;
import com.web.service.WalletService;
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
    void failedWebhookCancelsPendingOrder() {
        Payment payment = new Payment();
        payment.setOrderId(9L);
        payment.setTradeId("TRD123");
        payment.setStatus("PENDING");
        when(paymentMapper.getByTradeId("TRD123")).thenReturn(payment);
        when(paymentMapper.updateStatus(eq("TRD123"), eq("FAILED"), eq(null))).thenReturn(1);

        boolean success = paymentService.handleWebhook("TRD123", "FAILED");

        assertEquals(true, success);
        verify(orderService).updateOrderStatus(9L, 4);
    }
}
