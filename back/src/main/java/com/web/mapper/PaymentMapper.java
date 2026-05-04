package com.web.mapper;

import com.web.pojo.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PaymentMapper {
    Payment getByOrderId(Long orderId);
    
    Payment getByTradeId(String tradeId);
    
    int insert(Payment payment);
    
    int updateStatus(@Param("tradeId") String tradeId, @Param("status") String status, @Param("paidAt") java.time.LocalDateTime paidAt);

    int updatePendingStatusByOrderId(@Param("orderId") Long orderId,
                                     @Param("status") String status,
                                     @Param("paidAt") java.time.LocalDateTime paidAt);

    Map<String, Object> adminPaymentStats();

    List<Map<String, Object>> adminRecentPayments(@Param("limit") int limit);
}
