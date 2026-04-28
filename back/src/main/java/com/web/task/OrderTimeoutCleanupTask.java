package com.web.task;

import com.web.config.OrderCleanupProperties;
import com.web.mapper.OrderMapper;
import com.web.mapper.PaymentMapper;
import com.web.pojo.Order;
import com.web.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderTimeoutCleanupTask {

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final PaymentMapper paymentMapper;
    private final OrderCleanupProperties properties;

    public OrderTimeoutCleanupTask(OrderMapper orderMapper,
                                   OrderService orderService,
                                   PaymentMapper paymentMapper,
                                   OrderCleanupProperties properties) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.paymentMapper = paymentMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${order.cleanup.fixed-delay-ms:60000}",
            initialDelayString = "${order.cleanup.fixed-delay-ms:60000}")
    public void cleanupExpiredPendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(properties.getTimeoutMinutes());
        List<Order> expiredOrders = orderMapper.getExpiredPendingOrders(cutoff, properties.getBatchSize());
        for (Order expiredOrder : expiredOrders) {
            paymentMapper.updatePendingStatusByOrderId(expiredOrder.getId(), "FAILED", null);
            orderService.updateOrderStatus(expiredOrder.getId(), 4);
        }
    }
}
