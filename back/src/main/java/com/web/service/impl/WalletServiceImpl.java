package com.web.service.impl;

import com.web.exception.BusinessException;
import com.web.mapper.UserWalletMapper;
import com.web.mapper.WalletTransactionMapper;
import com.web.pojo.UserWallet;
import com.web.pojo.WalletTransaction;
import com.web.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WalletServiceImpl implements WalletService {

    private static final BigDecimal DEFAULT_BALANCE = new BigDecimal("20000.00");

    @Autowired
    private UserWalletMapper userWalletMapper;

    @Autowired
    private WalletTransactionMapper walletTransactionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserWallet getOrCreateWallet(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("UNAUTHORIZED", "请先登录");
        }
        userWalletMapper.insertIfAbsent(userId, DEFAULT_BALANCE);
        return userWalletMapper.getByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long userId, Long orderId, BigDecimal amount, String tradeId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("VALIDATION_FAILED", "支付金额必须大于 0");
        }
        getOrCreateWallet(userId);
        int rows = userWalletMapper.deductIfEnough(userId, amount);
        if (rows <= 0) {
            throw new BusinessException("INSUFFICIENT_BALANCE", "余额不足");
        }
        UserWallet wallet = userWalletMapper.getByUserId(userId);
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(userId);
        transaction.setOrderId(orderId);
        transaction.setTradeId(tradeId);
        transaction.setType("PAYMENT");
        transaction.setAmount(amount.negate());
        transaction.setBalanceAfter(wallet.getBalance());
        transaction.setRemark("订单余额支付");
        walletTransactionMapper.insert(transaction);
    }

    @Override
    public List<WalletTransaction> listTransactions(Long userId) {
        getOrCreateWallet(userId);
        return walletTransactionMapper.listByUserId(userId);
    }
}
