package com.web.service.payment;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.web.config.AlipayPaymentProperties;
import com.web.exception.BusinessException;
import com.web.pojo.Order;
import com.web.pojo.Payment;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AlipayPaymentGatewayImpl implements AlipayPaymentGateway {

    private final AlipayPaymentProperties properties;

    public AlipayPaymentGatewayImpl(AlipayPaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<String, Object> createPagePay(Payment payment, Order order) {
        if (!"sdk".equalsIgnoreCase(properties.getMode())) {
            return mockPayload(payment);
        }
        validateSdkConfig();

        DefaultAlipayClient client = new DefaultAlipayClient(
                properties.getGatewayUrl(),
                properties.getAppId(),
                properties.getMerchantPrivateKey(),
                properties.getFormat(),
                properties.getCharset(),
                properties.getAlipayPublicKey(),
                properties.getSignType());

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(properties.getNotifyUrl());
        request.setReturnUrl(properties.getReturnUrl());
        request.setBizContent(buildBizContent(payment, order));

        try {
            String body = client.pageExecute(request).getBody();
            Map<String, Object> result = new HashMap<>();
            result.put("mode", "sdk");
            result.put("gateway", "alipay");
            result.put("payForm", body);
            return result;
        } catch (AlipayApiException e) {
            throw new BusinessException("PAYMENT_FAILED", "支付宝支付单创建失败");
        }
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        if (!"sdk".equalsIgnoreCase(properties.getMode())) {
            return false;
        }
        if (isBlank(properties.getAlipayPublicKey())) {
            return false;
        }
        try {
            return AlipaySignature.rsaCheckV1(
                    params,
                    properties.getAlipayPublicKey(),
                    properties.getCharset(),
                    properties.getSignType());
        } catch (AlipayApiException e) {
            return false;
        }
    }

    private String buildBizContent(Payment payment, Order order) {
        JSONObject content = new JSONObject();
        content.put("out_trade_no", payment.getTradeId());
        content.put("total_amount", payment.getAmount().toPlainString());
        content.put("subject", properties.getSubjectPrefix() + " " + order.getOrderNo());
        content.put("product_code", "FAST_INSTANT_TRADE_PAY");
        return content.toJSONString();
    }

    private Map<String, Object> mockPayload(Payment payment) {
        Map<String, Object> result = new HashMap<>();
        result.put("mode", "mock");
        result.put("gateway", "alipay");
        result.put("paymentUrl", properties.getGatewayUrl() + "?mock=1&tradeId=" + payment.getTradeId());
        result.put("payForm", "");
        return result;
    }

    private void validateSdkConfig() {
        if (isBlank(properties.getGatewayUrl())
                || isBlank(properties.getAppId())
                || isBlank(properties.getMerchantPrivateKey())
                || isBlank(properties.getAlipayPublicKey())
                || isBlank(properties.getNotifyUrl())
                || isBlank(properties.getReturnUrl())) {
            throw new BusinessException("PAYMENT_CONFIG_INVALID", "支付宝支付配置不完整");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
