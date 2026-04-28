package com.web.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {
    private Long id;
    private Long userId;
    private Long orderId;
    private String tradeId;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String remark;
    private LocalDateTime createTime;
}
