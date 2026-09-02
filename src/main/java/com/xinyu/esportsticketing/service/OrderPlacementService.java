package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderPlacementService {

    private final OrderService orderService;
    private final RedisOrderService redisOrderService;
    private final String orderMode;

    public OrderPlacementService(
            OrderService orderService,
            RedisOrderService redisOrderService,
            @Value("${app.order.mode}") String orderMode) {

        if (!"mysql".equals(orderMode) && !"redis".equals(orderMode)) {
            throw new IllegalArgumentException(
                    "Unsupported app.order.mode: " + orderMode
            );
        }

        this.orderService = orderService;
        this.redisOrderService = redisOrderService;
        this.orderMode = orderMode;
    }

    public Order createOrder(Integer userId, Integer categoryId) {
        if ("redis".equals(orderMode)) {
            return redisOrderService.createOrder(userId, categoryId);
        }

        return orderService.createOrder(userId, categoryId);
    }
}
