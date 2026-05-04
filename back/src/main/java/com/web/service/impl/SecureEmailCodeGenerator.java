package com.web.service.impl;

import com.web.service.EmailCodeGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureEmailCodeGenerator implements EmailCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate(int length) {
        int safeLength = Math.max(4, Math.min(length, 8));
        StringBuilder code = new StringBuilder(safeLength);
        for (int i = 0; i < safeLength; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
