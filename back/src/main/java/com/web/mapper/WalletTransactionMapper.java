package com.web.mapper;

import com.web.pojo.WalletTransaction;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface WalletTransactionMapper {
    int insert(WalletTransaction transaction);

    List<WalletTransaction> listByUserId(Long userId);

    Map<String, Object> adminWalletStats();

    List<Map<String, Object>> adminRecentWalletTransactions(int limit);
}
