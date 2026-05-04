package com.web.service.impl;

import com.web.exception.BusinessException;
import com.web.mapper.OrderMapper;
import com.web.mapper.PaymentMapper;
import com.web.mapper.WalletTransactionMapper;
import com.web.service.AdminPaymentOverviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AdminPaymentOverviewServiceImpl implements AdminPaymentOverviewService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private WalletTransactionMapper walletTransactionMapper;

    @Override
    public Map<String, Object> getOverview(int limit) {
        if (limit <= 0 || limit > 100) {
            throw new BusinessException("VALIDATION_FAILED", "limit 必须在 1 到 100 之间");
        }

        Map<String, Object> stats = new HashMap<>();
        stats.putAll(orderMapper.adminOrderStats());
        stats.putAll(paymentMapper.adminPaymentStats());
        stats.putAll(walletTransactionMapper.adminWalletStats());

        Map<String, Object> result = new HashMap<>();
        result.put("stats", stats);
        result.put("recentOrders", orderMapper.adminRecentOrders(limit));
        result.put("recentPayments", paymentMapper.adminRecentPayments(limit));
        result.put("recentWalletTransactions", walletTransactionMapper.adminRecentWalletTransactions(limit));
        return result;
    }
}
