package com.web.service.impl;

import com.web.config.EmailVerificationProperties;
import com.web.exception.BusinessException;
import com.web.mapper.EmailVerificationCodeMapper;
import com.web.pojo.EmailVerificationCode;
import com.web.service.EmailCodeGenerator;
import com.web.service.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceImplTest {

    private EmailVerificationCodeMapper mapper;
    private EmailSender sender;
    private EmailCodeGenerator generator;
    private EmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(EmailVerificationCodeMapper.class);
        sender = mock(EmailSender.class);
        generator = mock(EmailCodeGenerator.class);
        EmailVerificationProperties properties = new EmailVerificationProperties();
        properties.setExpiresMinutes(10);
        properties.setResendSeconds(60);
        properties.setHourlyLimit(5);
        properties.setMaxAttempts(5);
        service = new EmailVerificationServiceImpl(mapper, sender, generator, properties);
    }

    @Test
    void requestRegisterCodeHashesCodeAndSendsEmail() {
        when(generator.generate(6)).thenReturn("123456");
        when(mapper.countCreatedSince(eq("user@example.com"), eq("REGISTER"), any(LocalDateTime.class))).thenReturn(0);

        service.requestCode(" User@Example.com ", "REGISTER", "127.0.0.1");

        ArgumentCaptor<EmailVerificationCode> captor = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(mapper).insert(captor.capture());
        EmailVerificationCode saved = captor.getValue();
        assertEquals("user@example.com", saved.getEmail());
        assertEquals("REGISTER", saved.getPurpose());
        assertEquals("127.0.0.1", saved.getSendIp());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
        assertTrue(saved.getCodeHash().length() >= 64);
        verify(sender).sendVerificationCode("user@example.com", "123456", 10, "REGISTER");
    }

    @Test
    void requestRegisterCodeRejectsResendBeforeCooldown() {
        EmailVerificationCode existing = new EmailVerificationCode();
        existing.setCreatedAt(LocalDateTime.now().minusSeconds(20));
        when(mapper.findLatestUsable(eq("user@example.com"), eq("REGISTER"), any(LocalDateTime.class))).thenReturn(existing);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.requestCode("user@example.com", "REGISTER", "127.0.0.1"));

        assertEquals("EMAIL_CODE_TOO_FREQUENT", exception.getCode());
        verify(sender, never()).sendVerificationCode(any(), any(), any(Integer.class), any());
    }

    @Test
    void verifyCodeMarksMatchingCodeUsed() {
        EmailVerificationCode existing = new EmailVerificationCode();
        existing.setId(7L);
        existing.setEmail("user@example.com");
        existing.setPurpose("REGISTER");
        existing.setCodeHash(EmailVerificationServiceImpl.hashCode("user@example.com", "REGISTER", "123456"));
        existing.setAttemptCount(0);
        existing.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(mapper.findLatestUsable(eq("user@example.com"), eq("REGISTER"), any(LocalDateTime.class))).thenReturn(existing);

        service.verifyCode("USER@example.com", "REGISTER", "123456");

        verify(mapper).markUsed(7L);
    }

    @Test
    void verifyCodeRejectsWrongCodeAndIncrementsAttempts() {
        EmailVerificationCode existing = new EmailVerificationCode();
        existing.setId(7L);
        existing.setEmail("user@example.com");
        existing.setPurpose("REGISTER");
        existing.setCodeHash(EmailVerificationServiceImpl.hashCode("user@example.com", "REGISTER", "123456"));
        existing.setAttemptCount(0);
        existing.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(mapper.findLatestUsable(eq("user@example.com"), eq("REGISTER"), any(LocalDateTime.class))).thenReturn(existing);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verifyCode("user@example.com", "REGISTER", "000000"));

        assertEquals("EMAIL_CODE_INVALID", exception.getCode());
        verify(mapper).incrementAttemptCount(7L);
    }
}
