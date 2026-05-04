package com.web.service;

public interface EmailVerificationService {
    void requestCode(String email, String purpose, String sendIp);

    void verifyCode(String email, String purpose, String code);
}
