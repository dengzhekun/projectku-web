package com.web.service.impl;

import com.web.exception.BusinessException;
import com.web.mapper.CartItemMapper;
import com.web.mapper.ProductMapper;
import com.web.pojo.CartItem;
import com.web.pojo.ProductSku;
import com.web.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartItemMapper cartItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Override
    public List<CartItem> getCartList(Long userId) {
        return cartItemMapper.getListByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addCartItem(Long userId, Long productId, Long skuId, Integer quantity) {
        validateQuantity(quantity);
        validateSkuBelongsToProduct(productId, skuId);
        CartItem existing = cartItemMapper.getByUserIdAndProductId(userId, productId, skuId);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            return cartItemMapper.update(existing) > 0;
        } else {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setProductId(productId);
            item.setSkuId(skuId);
            item.setQuantity(quantity);
            item.setChecked(1);
            return cartItemMapper.insert(item) > 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCartItemQuantity(Long userId, Long cartItemId, Integer quantity) {
        validateQuantity(quantity);
        CartItem item = new CartItem();
        item.setId(cartItemId);
        item.setUserId(userId);
        item.setQuantity(quantity);
        return cartItemMapper.update(item) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeCartItem(Long userId, Long cartItemId) {
        return cartItemMapper.delete(userId, cartItemId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean clearCheckedCart(Long userId) {
        return cartItemMapper.clearCheckedByUserId(userId) > 0;
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("VALIDATION_FAILED", "商品数量必须大于 0");
        }
    }

    private void validateSkuBelongsToProduct(Long productId, Long skuId) {
        if (skuId == null) {
            return;
        }
        ProductSku sku = productMapper.getSkuById(skuId);
        if (sku == null || productId == null || !productId.equals(sku.getProductId())) {
            throw new BusinessException("SKU_INVALID", "商品规格不属于当前商品");
        }
    }
}
