package com.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "payment.wechat")
public class WechatPaymentProperties {
    private String mode = "mock";
    private String appId = "";
    private String mchId = "";
    private String apiV3Key = "";
    private String merchantPrivateKey = "";
    private String merchantSerialNo = "";
    private String notifyUrl = "";
    private String returnUrl = "";
    private String descriptionPrefix = "元气购订单";
}
