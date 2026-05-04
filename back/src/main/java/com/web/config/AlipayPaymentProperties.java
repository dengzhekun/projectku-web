package com.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "payment.alipay")
public class AlipayPaymentProperties {
    private String mode = "mock";
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private String appId = "";
    private String merchantPrivateKey = "";
    private String alipayPublicKey = "";
    private String notifyUrl = "";
    private String returnUrl = "";
    private String signType = "RSA2";
    private String charset = "UTF-8";
    private String format = "json";
    private String subjectPrefix = "元气购订单";
}
