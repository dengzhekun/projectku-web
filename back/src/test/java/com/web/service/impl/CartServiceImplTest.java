package com.web.service.impl;

import com.web.exception.BusinessException;
import com.web.mapper.CartItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartItemMapper cartItemMapper;

    @InjectMocks
    private CartServiceImpl cartService;

    @Test
    void addCartItemRejectsNonPositiveQuantity() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cartService.addCartItem(1L, 2L, null, 0));

        assertEquals("VALIDATION_FAILED", exception.getCode());
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
