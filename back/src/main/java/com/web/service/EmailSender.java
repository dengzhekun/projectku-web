package com.web.service;

public interface EmailSender {
    void sendVerificationCode(String to, String code, int expiresMinutes, String purpose);
}
