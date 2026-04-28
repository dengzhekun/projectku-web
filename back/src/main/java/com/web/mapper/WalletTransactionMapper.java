package com.web.mapper;

import com.web.pojo.WalletTransaction;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WalletTransactionMapper {
    int insert(WalletTransaction transaction);

    List<WalletTransaction> listByUserId(Long userId);
}
