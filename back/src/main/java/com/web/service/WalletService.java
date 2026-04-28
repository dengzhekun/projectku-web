package com.web.service;

import com.web.pojo.UserWallet;
import com.web.pojo.WalletTransaction;

import java.math.BigDecimal;
import java.util.List;

public interface WalletService {
    UserWallet getOrCreateWallet(Long userId);

    void payOrder(Long userId, Long orderId, BigDecimal amount, String tradeId);

    List<WalletTransaction> listTransactions(Long userId);
}
