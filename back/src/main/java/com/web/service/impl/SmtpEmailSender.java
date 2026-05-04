package com.web.service.impl;

import com.web.config.EmailVerificationProperties;
import com.web.service.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "email.verification", name = "mode", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final EmailVerificationProperties properties;
    private final String from;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            EmailVerificationProperties properties,
            @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.from = from;
    }

    @Override
    public void sendVerificationCode(String to, String code, int expiresMinutes, String purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(properties.getSubjectPrefix());
        message.setText("您的验证码是：" + code + "\n\n"
                + "用途：" + displayPurpose(purpose) + "\n"
                + "有效期：" + expiresMinutes + " 分钟。\n"
                + "如果不是您本人操作，请忽略此邮件。");
        mailSender.send(message);
    }

    private String displayPurpose(String purpose) {
        return "REGISTER".equalsIgnoreCase(purpose) ? "注册账号" : purpose;
    }
}
