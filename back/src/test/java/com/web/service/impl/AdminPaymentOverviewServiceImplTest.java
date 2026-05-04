package com.web.service.impl;

import com.web.exception.BusinessException;
import com.web.mapper.OrderMapper;
import com.web.mapper.PaymentMapper;
import com.web.mapper.WalletTransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminPaymentOverviewServiceImplTest {

    private OrderMapper orderMapper;
    private PaymentMapper paymentMapper;
    private WalletTransactionMapper walletTransactionMapper;
    private AdminPaymentOverviewServiceImpl service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        paymentMapper = mock(PaymentMapper.class);
        walletTransactionMapper = mock(WalletTransactionMapper.class);
        service = new AdminPaymentOverviewServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "paymentMapper", paymentMapper);
        ReflectionTestUtils.setField(service, "walletTransactionMapper", walletTransactionMapper);
    }

    @Test
    void getOverviewRejectsTooLargeLimit() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.getOverview(101));

        assertEquals("VALIDATION_FAILED", exception.getCode());
    }

    @Test
    void getOverviewReturnsStatsAndRecentLists() {
        when(orderMapper.adminOrderStats()).thenReturn(Map.of("totalOrders", 2L));
        when(paymentMapper.adminPaymentStats()).thenReturn(Map.of("paidAmount", new BigDecimal("99.00")));
        when(walletTransactionMapper.adminWalletStats()).thenReturn(Map.of("walletPaymentAmount", new BigDecimal("-99.00")));
        when(orderMapper.adminRecentOrders(10)).thenReturn(List.of(Map.of("id", 1L)));
        when(paymentMapper.adminRecentPayments(10)).thenReturn(List.of(Map.of("tradeId", "TRD1")));
        when(walletTransactionMapper.adminRecentWalletTransactions(10)).thenReturn(List.of(Map.of("type", "PAYMENT")));

        Map<String, Object> result = service.getOverview(10);

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) result.get("stats");
        assertEquals(2L, stats.get("totalOrders"));
        assertEquals(new BigDecimal("99.00"), stats.get("paidAmount"));
        assertEquals(new BigDecimal("-99.00"), stats.get("walletPaymentAmount"));
        assertEquals(1, ((List<?>) result.get("recentOrders")).size());
        assertEquals(1, ((List<?>) result.get("recentPayments")).size());
        assertEquals(1, ((List<?>) result.get("recentWalletTransactions")).size());
    }
}
