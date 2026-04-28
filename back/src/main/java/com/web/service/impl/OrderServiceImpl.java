package com.web.service.impl;

import cn.hutool.core.util.IdUtil;
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
import com.web.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private CouponService couponService;

    @Override
    public Order getOrderById(Long id) {
        return orderMapper.getById(id);
    }

    @Override
    public Order getOrderByOrderNo(String orderNo) {
        return orderMapper.getByOrderNo(orderNo);
    }

    @Override
    public List<Order> getOrderList(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        return orderMapper.getListByUserId(userId, offset, size);
    }

    @Override
    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemMapper.getListByOrderId(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkout(Long userId, Long addressId, String couponCode) {
        List<CartItem> cartItems = cartItemMapper.getListByUserId(userId);
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BusinessException("CART_EMPTY", "购物车为空");
        }

        List<CartItem> checkedItems = cartItems.stream()
                .filter(item -> item.getChecked() == 1)
                .toList();
        if (checkedItems.isEmpty()) {
            checkedItems = cartItems;
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : checkedItems) {
            Product product = productMapper.getById(cartItem.getProductId());
            if (product == null) {
                throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在");
            }

            reserveStock(cartItem, product);

            BigDecimal itemTotal = product.getPrice().multiply(new BigDecimal(cartItem.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setSkuId(cartItem.getSkuId());
            orderItem.setProductName(product.getName());
            orderItem.setPrice(product.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setTotalAmount(itemTotal);
            orderItem.setProductImage("/product_" + product.getId() + ".jpg");
            orderItems.add(orderItem);
        }

        String normalizedCouponCode = couponCode == null ? "" : couponCode.trim();
        BigDecimal discountAmount = resolveDiscount(userId, normalizedCouponCode, totalAmount);
        BigDecimal payAmount = totalAmount.subtract(discountAmount);
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
            payAmount = BigDecimal.ZERO;
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(IdUtil.getSnowflakeNextIdStr());
        order.setTotalAmount(totalAmount);
        order.setPayAmount(payAmount);
        order.setStatus(0);
        order.setAddressId(addressId);
        orderMapper.insert(order);

        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
        }
        orderItemMapper.insertBatch(orderItems);

        if (!normalizedCouponCode.isEmpty() && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            boolean used = couponService.useCoupon(userId, normalizedCouponCode, totalAmount);
            if (!used) {
                throw new BusinessException("COUPON_INVALID", "优惠券使用失败，请稍后重试");
            }
        }

        cartItemMapper.clearCheckedByUserId(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("orderItems", orderItems);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long id, Long userId) {
        Order order = orderMapper.getById(id);
        if (order != null && order.getUserId().equals(userId) && order.getStatus() == 0) {
            boolean updated = orderMapper.updateStatusIfCurrent(id, 0, 4) > 0;
            if (updated) {
                paymentMapper.updatePendingStatusByOrderId(id, "FAILED", null);
                restoreOrderStock(id);
            }
            return updated;
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrderStatus(Long id, Integer status) {
        Order order = orderMapper.getById(id);
        if (order == null) {
            return false;
        }

        if (order.getStatus().equals(status)) {
            return true;
        }

        boolean success;
        if (status == 1 || status == 4) {
            if (order.getStatus() != 0) {
                return false;
            }
            success = orderMapper.updateStatusIfCurrent(id, 0, status) > 0;
        } else {
            success = orderMapper.updateStatus(id, status) > 0;
        }
        if (!success) {
            return false;
        }

        if (status == 1) {
            List<OrderItem> items = orderItemMapper.getListByOrderId(id);
            for (OrderItem item : items) {
                productMapper.increaseSold(item.getProductId(), item.getQuantity());
            }
        } else if (status == 4 && order.getStatus() == 0) {
            paymentMapper.updatePendingStatusByOrderId(id, "FAILED", null);
            restoreOrderStock(id);
        }

        return true;
    }

    private void reserveStock(CartItem cartItem, Product product) {
        if (cartItem.getSkuId() != null) {
            int affectedSku = productMapper.decreaseSkuStockIfEnough(cartItem.getSkuId(), cartItem.getQuantity());
            if (affectedSku <= 0) {
                throw new BusinessException("STOCK_NOT_ENOUGH", "库存不足");
            }
        }

        int affectedProduct = productMapper.decreaseStockIfEnough(product.getId(), cartItem.getQuantity());
        if (affectedProduct <= 0) {
            throw new BusinessException("STOCK_NOT_ENOUGH", "库存不足");
        }
    }

    private BigDecimal resolveDiscount(Long userId, String couponCode, BigDecimal totalAmount) {
        if (couponCode.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<String, Object> couponCheck = couponService.checkCoupon(userId, couponCode, totalAmount);
        if (!Boolean.TRUE.equals(couponCheck.get("valid"))) {
            throw new BusinessException("COUPON_INVALID", String.valueOf(couponCheck.get("reason")));
        }

        Object discountValue = couponCheck.get("discount");
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discountValue instanceof BigDecimal) {
            discountAmount = (BigDecimal) discountValue;
        } else if (discountValue != null) {
            discountAmount = new BigDecimal(discountValue.toString());
        }

        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (discountAmount.compareTo(totalAmount) > 0) {
            return totalAmount;
        }
        return discountAmount;
    }

    private void restoreOrderStock(Long orderId) {
        List<OrderItem> items = orderItemMapper.getListByOrderId(orderId);
        for (OrderItem item : items) {
            if (item.getSkuId() != null) {
                productMapper.increaseSkuStock(item.getSkuId(), item.getQuantity());
            }
            productMapper.increaseStock(item.getProductId(), item.getQuantity());
        }
    }
}
