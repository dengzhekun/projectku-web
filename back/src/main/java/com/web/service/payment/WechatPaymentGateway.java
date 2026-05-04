package com.web.service.payment;

import com.web.pojo.Order;
import com.web.pojo.Payment;

import java.util.Map;

public interface WechatPaymentGateway {
    Map<String, Object> createNativePay(Payment payment, Order order);
}
