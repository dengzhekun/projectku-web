package com.web.controller;

import com.web.service.AdminPaymentOverviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPaymentControllerTest {

    private AdminPaymentOverviewService overviewService;
    private AdminPaymentController controller;

    @BeforeEach
    void setUp() {
        overviewService = mock(AdminPaymentOverviewService.class);
        controller = new AdminPaymentController();
        ReflectionTestUtils.setField(controller, "overviewService", overviewService);
    }

    @Test
    void overviewReturnsStatsAndRecentRows() {
        Map<String, Object> overview = Map.of(
                "stats", Map.of("paidAmount", new BigDecimal("99.00")),
                "recentOrders", List.of(Map.of("id", 1L)),
                "recentPayments", List.of(Map.of("tradeId", "TRD1")),
                "recentWalletTransactions", List.of(Map.of("type", "PAYMENT")));
        when(overviewService.getOverview(20)).thenReturn(overview);

        Map<String, Object> body = controller.overview(20).getBody();

        assertEquals(200, body.get("code"));
        assertEquals(overview, body.get("data"));
        verify(overviewService).getOverview(20);
    }
}
