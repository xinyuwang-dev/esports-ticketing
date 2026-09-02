package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {

    @Mock
    private OrderService orderService;

    @Mock
    private RedisOrderService redisOrderService;

    @Test
    void shouldUseOrderServiceInMysqlMode() {
        Order expectedOrder = new Order();

        when(orderService.createOrder(1, 3))
                .thenReturn(expectedOrder);

        OrderPlacementService orderPlacementService =
                new OrderPlacementService(
                        orderService,
                        redisOrderService,
                        "mysql"
                );

        Order result = orderPlacementService.createOrder(1, 3);

        assertSame(expectedOrder, result);
        verify(orderService).createOrder(1, 3);
        verify(redisOrderService, never()).createOrder(1, 3);
    }

    @Test
    void shouldUseRedisOrderServiceInRedisMode() {
        Order expectedOrder = new Order();

        when(redisOrderService.createOrder(1, 3))
                .thenReturn(expectedOrder);

        OrderPlacementService orderPlacementService =
                new OrderPlacementService(
                        orderService,
                        redisOrderService,
                        "redis"
                );

        Order result = orderPlacementService.createOrder(1, 3);

        assertSame(expectedOrder, result);
        verify(redisOrderService).createOrder(1, 3);
        verify(orderService, never()).createOrder(1, 3);
    }

    @Test
    void shouldRejectUnsupportedOrderMode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OrderPlacementService(
                        orderService,
                        redisOrderService,
                        "invalid"
                )
        );

        assertEquals(
                "Unsupported app.order.mode: invalid",
                exception.getMessage()
        );
        verifyNoInteractions(orderService, redisOrderService);
    }
}
