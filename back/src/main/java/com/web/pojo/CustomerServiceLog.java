package com.web.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerServiceLog {
    private Long id;
    private String queryText;
    private String conversationId;
    private String route;
    private String sourceType;
    private String sourceId;
    private BigDecimal confidence;
    private String fallbackReason;
    private LocalDateTime createdAt;
}
