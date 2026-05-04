package com.web.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationCode {
    private Long id;
    private String email;
    private String purpose;
    private String codeHash;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private Integer attemptCount;
    private String sendIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
