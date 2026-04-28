package com.web.service.impl;

import com.web.exception.BusinessException;
import com.web.mapper.CartItemMapper;
import com.web.mapper.OrderItemMapper;
import com.web.mapper.OrderMapper;
import com.web.mapper.PaymentMapper;
import com.web.mapper.ProductMapper;
import com.web.pojo.CartItem;
import com.web.pojo.Order;
import com.web.pojo.OrderItem;
import com.web.pojo.Product;
import com.web.service.CouponService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private CartItemMapper cartItemMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private CouponService couponService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void checkoutAppliesCouponDiscountWhenThresholdMet() {
        CartItem cartItem = new CartItem();
        cartItem.setUserId(1L);
        cartItem.setProductId(10L);
        cartItem.setQuantity(1);
        cartItem.setChecked(1);

        Product product = new Product();
        product.setId(10L);
        product.setName("测试商品");
        product.setPrice(new BigDecimal("3000.00"));
        product.setStock(10);

        Map<String, Object> couponCheck = new HashMap<>();
        couponCheck.put("valid", true);
        couponCheck.put("discount", new BigDecimal("300.00"));
        couponCheck.put("reason", null);

        when(cartItemMapper.getListByUserId(1L)).thenReturn(List.of(cartItem));
        when(productMapper.getById(10L)).thenReturn(product);
        when(couponService.checkCoupon(eq(1L), eq("SAVE300"), eq(new BigDecimal("3000.00"))))
                .thenReturn(couponCheck);
        when(couponService.useCoupon(eq(1L), eq("SAVE300"), eq(new BigDecimal("3000.00"))))
                .thenReturn(true);
        when(productMapper.decreaseStockIfEnough(10L, 1)).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(99L);
            return 1;
        });

        Map<String, Object> result = orderService.checkout(1L, 0L, "SAVE300");

        Order order = (Order) result.get("order");
        assertEquals(0, order.getPayAmount().compareTo(new BigDecimal("2700.00")));
        verify(couponService).useCoupon(1L, "SAVE300", new BigDecimal("3000.00"));
    }

    @Test
    void checkoutRejectsCouponWhenThresholdNotMet() {
        CartItem cartItem = new CartItem();
        cartItem.setUserId(1L);
        cartItem.setProductId(10L);
        cartItem.setQuantity(1);
        cartItem.setChecked(1);

        Product product = new Product();
        product.setId(10L);
        product.setName("测试商品");
        product.setPrice(new BigDecimal("2999.00"));
        product.setStock(10);

        Map<String, Object> couponCheck = new HashMap<>();
        couponCheck.put("valid", false);
        couponCheck.put("reason", "未达到优惠券使用门槛金额");

        when(cartItemMapper.getListByUserId(1L)).thenReturn(List.of(cartItem));
        when(productMapper.getById(10L)).thenReturn(product);
        when(productMapper.decreaseStockIfEnough(10L, 1)).thenReturn(1);
        when(couponService.checkCoupon(eq(1L), eq("SAVE300"), eq(new BigDecimal("2999.00"))))
                .thenReturn(couponCheck);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.checkout(1L, 0L, "SAVE300"));

        assertEquals("COUPON_INVALID", exception.getCode());
        assertEquals("未达到优惠券使用门槛金额", exception.getMessage());
    }

    @Test
    void checkoutRejectsWhenAtomicStockDeductionFails() {
        CartItem cartItem = new CartItem();
        cartItem.setUserId(1L);
        cartItem.setProductId(10L);
        cartItem.setQuantity(1);
        cartItem.setChecked(1);

        Product product = new Product();
        product.setId(10L);
        product.setName("测试商品");
        product.setPrice(new BigDecimal("2999.00"));
        product.setStock(1);

        when(cartItemMapper.getListByUserId(1L)).thenReturn(List.of(cartItem));
        when(productMapper.getById(10L)).thenReturn(product);
        when(productMapper.decreaseStockIfEnough(10L, 1)).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> orderService.checkout(1L, 0L, ""));

        assertEquals("STOCK_NOT_ENOUGH", exception.getCode());
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void cancelOrderRestoresReservedStock() {
        Order order = new Order();
        order.setId(88L);
        order.setUserId(1L);
        order.setStatus(0);

        OrderItem item = new OrderItem();
        item.setOrderId(88L);
        item.setProductId(10L);
        item.setSkuId(100L);
        item.setQuantity(2);

        when(orderMapper.getById(88L)).thenReturn(order);
        when(orderMapper.updateStatusIfCurrent(88L, 0, 4)).thenReturn(1);
        when(orderItemMapper.getListByOrderId(88L)).thenReturn(List.of(item));
        when(paymentMapper.updatePendingStatusByOrderId(88L, "FAILED", null)).thenReturn(1);
        when(productMapper.increaseSkuStock(100L, 2)).thenReturn(1);
        when(productMapper.increaseStock(10L, 2)).thenReturn(1);

        boolean success = orderService.cancelOrder(88L, 1L);

        assertEquals(true, success);
        verify(paymentMapper).updatePendingStatusByOrderId(88L, "FAILED", null);
        verify(productMapper).increaseSkuStock(100L, 2);
        verify(productMapper).increaseStock(10L, 2);
    }

    @Test
    void updateOrderStatusDoesNotReopenCancelledOrder() {
        Order order = new Order();
        order.setId(88L);
        order.setUserId(1L);
        order.setStatus(4);

        when(orderMapper.getById(88L)).thenReturn(order);

        boolean success = orderService.updateOrderStatus(88L, 1);

        assertFalse(success);
        verify(orderMapper, never()).updateStatus(88L, 1);
        verify(orderMapper, never()).updateStatusIfCurrent(88L, 0, 1);
        verify(orderItemMapper, never()).getListByOrderId(88L);
    }
}
