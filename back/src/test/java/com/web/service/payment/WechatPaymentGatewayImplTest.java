package com.web.service.payment;

import com.web.config.WechatPaymentProperties;
import com.web.exception.BusinessException;
import com.web.pojo.Order;
import com.web.pojo.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WechatPaymentGatewayImplTest {

    @Test
    void mockModeReturnsNativeMockPayload() {
        WechatPaymentProperties properties = new WechatPaymentProperties();
        properties.setMode("mock");
        WechatPaymentGatewayImpl gateway = new WechatPaymentGatewayImpl(properties);

        Payment payment = new Payment();
        payment.setTradeId("TRD123");
        payment.setAmount(new BigDecimal("99.00"));
        Order order = new Order();
        order.setOrderNo("ORDER123");

        var result = gateway.createNativePay(payment, order);

        assertEquals("mock", result.get("mode"));
        assertEquals("wechat", result.get("gateway"));
        assertEquals("weixin://wxpay/mock/TRD123", result.get("codeUrl"));
    }

    @Test
    void sdkModeRequiresCompleteConfig() {
        WechatPaymentProperties properties = new WechatPaymentProperties();
        properties.setMode("sdk");
        WechatPaymentGatewayImpl gateway = new WechatPaymentGatewayImpl(properties);

        Payment payment = new Payment();
        payment.setTradeId("TRD123");
        payment.setAmount(new BigDecimal("99.00"));
        Order order = new Order();
        order.setOrderNo("ORDER123");

        BusinessException exception = assertThrows(BusinessException.class, () -> gateway.createNativePay(payment, order));
        assertEquals("PAYMENT_CONFIG_INVALID", exception.getCode());
    }
}
