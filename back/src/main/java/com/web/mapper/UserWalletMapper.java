package com.web.mapper;

import com.web.pojo.UserWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface UserWalletMapper {
    UserWallet getByUserId(Long userId);

    int insertIfAbsent(@Param("userId") Long userId, @Param("balance") BigDecimal balance);

    int deductIfEnough(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
