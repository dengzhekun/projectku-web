package com.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "email.verification")
public class EmailVerificationProperties {

    private String mode = "console";
    private int codeLength = 6;
    private int expiresMinutes = 10;
    private int resendSeconds = 60;
    private int hourlyLimit = 5;
    private int maxAttempts = 5;
    private String senderName = "元气购";
    private String subjectPrefix = "元气购验证码";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(int codeLength) {
        this.codeLength = codeLength;
    }

    public int getExpiresMinutes() {
        return expiresMinutes;
    }

    public void setExpiresMinutes(int expiresMinutes) {
        this.expiresMinutes = expiresMinutes;
    }

    public int getResendSeconds() {
        return resendSeconds;
    }

    public void setResendSeconds(int resendSeconds) {
        this.resendSeconds = resendSeconds;
    }

    public int getHourlyLimit() {
        return hourlyLimit;
    }

    public void setHourlyLimit(int hourlyLimit) {
        this.hourlyLimit = hourlyLimit;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSubjectPrefix() {
        return subjectPrefix;
    }

    public void setSubjectPrefix(String subjectPrefix) {
        this.subjectPrefix = subjectPrefix;
    }
}
