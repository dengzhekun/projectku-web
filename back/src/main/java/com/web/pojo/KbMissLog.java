package com.web.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KbMissLog {
    private Long id;
    private String queryText;
    private String conversationId;
    private BigDecimal confidence;
    private String fallbackReason;
    private String status;
    private LocalDateTime createdAt;
}
