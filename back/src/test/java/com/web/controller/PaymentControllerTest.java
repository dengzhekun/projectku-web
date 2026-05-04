package com.web.controller;

import com.web.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerTest {

    private PaymentService paymentService;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        controller = new PaymentController();
        ReflectionTestUtils.setField(controller, "paymentService", paymentService);
    }

    @Test
    void alipayNotifyPassesFormParamsToPaymentServiceAndReturnsSuccessText() {
        when(paymentService.handleAlipayNotify(argThat(params ->
                "TRD123".equals(params.get("out_trade_no"))
                        && "88.50".equals(params.get("total_amount"))
                        && "TRADE_SUCCESS".equals(params.get("trade_status"))))).thenReturn(true);

        String body = controller.alipayNotify(Map.of(
                "out_trade_no", "TRD123",
                "total_amount", "88.50",
                "trade_status", "TRADE_SUCCESS"));

        assertEquals("success", body);
        verify(paymentService).handleAlipayNotify(argThat(params ->
                "TRD123".equals(params.get("out_trade_no"))));
    }

    @Test
    void alipayNotifyReturnsFailureTextWhenServiceRejectsCallback() {
        when(paymentService.handleAlipayNotify(Map.of("out_trade_no", "TRD404"))).thenReturn(false);

        String body = controller.alipayNotify(Map.of("out_trade_no", "TRD404"));

        assertEquals("failure", body);
    }
}
