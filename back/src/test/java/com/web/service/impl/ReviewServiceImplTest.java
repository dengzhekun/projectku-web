package com.web.service.impl;

import com.web.exception.BusinessException;
import com.web.mapper.ReviewMapper;
import com.web.pojo.Order;
import com.web.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void createRejectsMissingOrderId() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> reviewService.create(1L, null, 2L, 5, "ok", "[]"));

        assertEquals("VALIDATION_FAILED", exception.getCode());
        verifyNoInteractions(orderService, reviewMapper);
    }

    @Test
    void createRejectsRatingOutOfRange() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> reviewService.create(1L, 1L, 2L, 6, "ok", "[]"));

        assertEquals("VALIDATION_FAILED", exception.getCode());
        verifyNoInteractions(orderService, reviewMapper);
    }

    @Test
    void createRejectsOrderNotBelongToUser() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(2L);
        when(orderService.getOrderById(1L)).thenReturn(order);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> reviewService.create(1L, 1L, 2L, 5, "ok", "[]"));

        assertEquals("ORDER_NOT_FOUND", exception.getCode());
        verify(orderService).getOrderById(1L);
        verifyNoInteractions(reviewMapper);
    }
}

