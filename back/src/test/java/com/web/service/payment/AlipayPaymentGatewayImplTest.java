package com.web.service.payment;

import com.web.config.AlipayPaymentProperties;
import com.web.exception.BusinessException;
import com.web.pojo.Order;
import com.web.pojo.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlipayPaymentGatewayImplTest {

    @Test
    void mockModeReturnsMockPayloadWhenSdkIsNotEnabled() {
        AlipayPaymentProperties properties = new AlipayPaymentProperties();
        properties.setMode("mock");
        properties.setGatewayUrl("https://sandbox.example.com/gateway.do");
        AlipayPaymentGatewayImpl gateway = new AlipayPaymentGatewayImpl(properties);

        Payment payment = new Payment();
        payment.setTradeId("TRD123");
        payment.setAmount(new BigDecimal("99.00"));
        Order order = new Order();
        order.setOrderNo("ORDER123");

        var result = gateway.createPagePay(payment, order);

        assertEquals("mock", result.get("mode"));
        assertEquals("alipay", result.get("gateway"));
    }

    @Test
    void sdkModeRequiresCompleteConfig() {
        AlipayPaymentProperties properties = new AlipayPaymentProperties();
        properties.setMode("sdk");
        AlipayPaymentGatewayImpl gateway = new AlipayPaymentGatewayImpl(properties);

        Payment payment = new Payment();
        payment.setTradeId("TRD123");
        payment.setAmount(new BigDecimal("99.00"));
        Order order = new Order();
        order.setOrderNo("ORDER123");

        BusinessException exception = assertThrows(BusinessException.class, () -> gateway.createPagePay(payment, order));
        assertEquals("PAYMENT_CONFIG_INVALID", exception.getCode());
    }

    @Test
    void verifyNotifyReturnsFalseWhenSdkConfigIsIncomplete() {
        AlipayPaymentProperties properties = new AlipayPaymentProperties();
        properties.setMode("sdk");
        AlipayPaymentGatewayImpl gateway = new AlipayPaymentGatewayImpl(properties);

        assertFalse(gateway.verifyNotify(Map.of("out_trade_no", "TRD123")));
    }
}
