package com.web.service.payment;

import com.web.config.WechatPaymentProperties;
import com.web.exception.BusinessException;
import com.web.pojo.Order;
import com.web.pojo.Payment;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WechatPaymentGatewayImpl implements WechatPaymentGateway {

    private final WechatPaymentProperties properties;

    public WechatPaymentGatewayImpl(WechatPaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<String, Object> createNativePay(Payment payment, Order order) {
        if (!"sdk".equalsIgnoreCase(properties.getMode())) {
            return mockPayload(payment);
        }
        validateSdkConfig();
        throw new BusinessException("PAYMENT_GATEWAY_NOT_READY", "微信支付 SDK 适配层已预留，请接入商户证书后启用");
    }

    private Map<String, Object> mockPayload(Payment payment) {
        Map<String, Object> result = new HashMap<>();
        result.put("mode", "mock");
        result.put("gateway", "wechat");
        result.put("codeUrl", "weixin://wxpay/mock/" + payment.getTradeId());
        result.put("qr", "https://mock-payment-gateway.com/wechat/native/" + payment.getTradeId());
        return result;
    }

    private void validateSdkConfig() {
        if (isBlank(properties.getAppId())
                || isBlank(properties.getMchId())
                || isBlank(properties.getApiV3Key())
                || isBlank(properties.getMerchantPrivateKey())
                || isBlank(properties.getMerchantSerialNo())
                || isBlank(properties.getNotifyUrl())) {
            throw new BusinessException("PAYMENT_CONFIG_INVALID", "微信支付配置不完整");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
