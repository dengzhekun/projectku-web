package com.web.service.payment;

import com.web.pojo.Order;
import com.web.pojo.Payment;

import java.util.Map;

public interface AlipayPaymentGateway {
    Map<String, Object> createPagePay(Payment payment, Order order);

    boolean verifyNotify(Map<String, String> params);
}
