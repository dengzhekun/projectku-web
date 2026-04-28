package com.web.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserWallet {
    private Long id;
    private Long userId;
    private BigDecimal balance;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
