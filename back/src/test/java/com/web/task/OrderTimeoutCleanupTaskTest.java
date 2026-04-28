package com.web.task;

import com.web.config.OrderCleanupProperties;
import com.web.mapper.OrderMapper;
import com.web.mapper.PaymentMapper;
import com.web.pojo.Order;
import com.web.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutCleanupTaskTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private OrderCleanupProperties properties;

    @InjectMocks
    private OrderTimeoutCleanupTask task;

    @Test
    void cleanupCancelsExpiredPendingOrdersAndMarksPendingPaymentFailed() {
        Order expiredOrder = new Order();
        expiredOrder.setId(88L);
        expiredOrder.setStatus(0);
        expiredOrder.setCreateTime(LocalDateTime.now().minusMinutes(40));

        when(properties.getTimeoutMinutes()).thenReturn(30);
        when(properties.getBatchSize()).thenReturn(100);
        when(orderMapper.getExpiredPendingOrders(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(expiredOrder));

        task.cleanupExpiredPendingOrders();

        verify(paymentMapper).updatePendingStatusByOrderId(88L, "FAILED", null);
        verify(orderService).updateOrderStatus(88L, 4);
    }
}
