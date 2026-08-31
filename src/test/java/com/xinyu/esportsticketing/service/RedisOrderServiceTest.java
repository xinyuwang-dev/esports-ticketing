package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisOrderServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private RedisStockService redisStockService;

    @InjectMocks
    private RedisOrderService redisOrderService;

    @Test
    void shouldRejectOrderWhenRedisStockIsEmpty() {

        when(redisStockService.decreaseStock(3))
                .thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> redisOrderService.createOrder(1, 3)
        );

        verify(orderService, never())
                .createOrder(1, 3);

        verify(redisStockService, never())
                .increaseStock(3);
    }

    @Test
    void shouldRestoreRedisStockWhenOrderCreationFails() {

        when(redisStockService.decreaseStock(3))
                .thenReturn(true);

        when(orderService.createOrder(1, 3))
                .thenThrow(new RuntimeException("Database error"));

        assertThrows(
                RuntimeException.class,
                () -> redisOrderService.createOrder(1, 3)
        );

        verify(orderService)
                .createOrder(1, 3);

        verify(redisStockService)
                .increaseStock(3);
    }

    @Test
    void shouldCreateOrderWhenRedisStockIsAvailable() {

        Order order = new Order();

        when(redisStockService.decreaseStock(3))
                .thenReturn(true);

        when(orderService.createOrder(1, 3))
                .thenReturn(order);

        Order result =
                redisOrderService.createOrder(1, 3);

        assertSame(order, result);

        verify(orderService)
                .createOrder(1, 3);

        verify(redisStockService, never())
                .increaseStock(3);
    }
}