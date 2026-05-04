package com.web.service.impl;

import com.web.service.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "email.verification", name = "mode", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    @Override
    public void sendVerificationCode(String to, String code, int expiresMinutes, String purpose) {
        log.info("Email verification code [{}] for {} purpose={} expiresIn={}min", code, to, purpose, expiresMinutes);
    }
}
