package com.web.service.impl;

import com.web.exception.BusinessException;
import com.web.mapper.CartItemMapper;
import com.web.mapper.ProductMapper;
import com.web.pojo.ProductSku;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartItemMapper cartItemMapper;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private CartServiceImpl cartService;

    @Test
    void addCartItemRejectsNonPositiveQuantity() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cartService.addCartItem(1L, 2L, null, 0));

        assertEquals("VALIDATION_FAILED", exception.getCode());
        verifyNoInteractions(cartItemMapper);
        verifyNoInteractions(productMapper);
    }

    @Test
    void addCartItemRejectsSkuThatDoesNotBelongToProduct() {
        ProductSku sku = new ProductSku();
        sku.setId(100L);
        sku.setProductId(99L);
        when(productMapper.getSkuById(100L)).thenReturn(sku);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cartService.addCartItem(1L, 2L, 100L, 1));

        assertEquals("SKU_INVALID", exception.getCode());
        verifyNoInteractions(cartItemMapper);
    }

    @Test
    void updateCartItemQuantityRejectsNonPositiveQuantity() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cartService.updateCartItemQuantity(1L, 10L, -3));

        assertEquals("VALIDATION_FAILED", exception.getCode());
        verifyNoInteractions(cartItemMapper);
    }
}
