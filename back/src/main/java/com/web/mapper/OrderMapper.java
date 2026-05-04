package com.web.mapper;

import com.web.pojo.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    Order getById(Long id);
    
    Order getByOrderNo(String orderNo);
    
    List<Order> getListByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);
    
    int insert(Order order);
    
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("expectedStatus") Integer expectedStatus,
                              @Param("status") Integer status);

    List<Order> getExpiredPendingOrders(@Param("cutoff") LocalDateTime cutoff,
                                        @Param("limit") int limit);

    Map<String, Object> adminOrderStats();

    List<Map<String, Object>> adminRecentOrders(@Param("limit") int limit);
}
