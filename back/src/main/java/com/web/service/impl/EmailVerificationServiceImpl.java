package com.web.service.impl;

import com.web.config.EmailVerificationProperties;
import com.web.exception.BusinessException;
import com.web.mapper.EmailVerificationCodeMapper;
import com.web.pojo.EmailVerificationCode;
import com.web.service.EmailCodeGenerator;
import com.web.service.EmailSender;
import com.web.service.EmailVerificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final EmailVerificationCodeMapper mapper;
    private final EmailSender sender;
    private final EmailCodeGenerator generator;
    private final EmailVerificationProperties properties;

    public EmailVerificationServiceImpl(
            EmailVerificationCodeMapper mapper,
            EmailSender sender,
            EmailCodeGenerator generator,
            EmailVerificationProperties properties) {
        this.mapper = mapper;
        this.sender = sender;
        this.generator = generator;
        this.properties = properties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void requestCode(String email, String purpose, String sendIp) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedPurpose = normalizePurpose(purpose);
        LocalDateTime now = LocalDateTime.now();

        int recentCount = mapper.countCreatedSince(normalizedEmail, normalizedPurpose, now.minusHours(1));
        if (recentCount >= properties.getHourlyLimit()) {
            throw new BusinessException("EMAIL_CODE_LIMITED", "验证码发送次数过多，请稍后再试");
        }

        EmailVerificationCode latest = mapper.findLatestUsable(normalizedEmail, normalizedPurpose, now);
        if (latest != null && latest.getCreatedAt() != null) {
            long seconds = Duration.between(latest.getCreatedAt(), now).getSeconds();
            if (seconds < properties.getResendSeconds()) {
                throw new BusinessException("EMAIL_CODE_TOO_FREQUENT", "验证码发送太频繁，请稍后再试");
            }
        }

        String code = generator.generate(properties.getCodeLength());
        EmailVerificationCode record = new EmailVerificationCode();
        record.setEmail(normalizedEmail);
        record.setPurpose(normalizedPurpose);
        record.setCodeHash(hashCode(normalizedEmail, normalizedPurpose, code));
        record.setExpiresAt(now.plusMinutes(properties.getExpiresMinutes()));
        record.setAttemptCount(0);
        record.setSendIp(sendIp);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        mapper.insert(record);

        sender.sendVerificationCode(normalizedEmail, code, properties.getExpiresMinutes(), normalizedPurpose);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyCode(String email, String purpose, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedPurpose = normalizePurpose(purpose);
        if (code == null || !code.trim().matches("\\d{4,8}")) {
            throw new BusinessException("EMAIL_CODE_INVALID", "验证码不正确");
        }

        LocalDateTime now = LocalDateTime.now();
        EmailVerificationCode latest = mapper.findLatestUsable(normalizedEmail, normalizedPurpose, now);
        if (latest == null) {
            throw new BusinessException("EMAIL_CODE_EXPIRED", "验证码不存在或已过期");
        }
        if (latest.getAttemptCount() != null && latest.getAttemptCount() >= properties.getMaxAttempts()) {
            throw new BusinessException("EMAIL_CODE_LOCKED", "验证码错误次数过多，请重新获取");
        }

        String expectedHash = hashCode(normalizedEmail, normalizedPurpose, code.trim());
        if (!expectedHash.equals(latest.getCodeHash())) {
            mapper.incrementAttemptCount(latest.getId());
            throw new BusinessException("EMAIL_CODE_INVALID", "验证码不正确");
        }
        mapper.markUsed(latest.getId());
    }

    public static String hashCode(String email, String purpose, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = normalizeEmail(email) + ":" + normalizePurpose(purpose) + ":" + code.trim();
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException("VALIDATION_FAILED", "邮箱不能为空");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessException("VALIDATION_FAILED", "邮箱格式不正确");
        }
        return normalized;
    }

    private static String normalizePurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return "REGISTER";
        }
        return purpose.trim().toUpperCase(Locale.ROOT);
    }
}
