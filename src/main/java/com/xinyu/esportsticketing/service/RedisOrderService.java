package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.Order;
import org.springframework.stereotype.Service;

@Service
public class RedisOrderService {

    private final OrderService orderService;
    private final RedisStockService redisStockService;

    public RedisOrderService(
            OrderService orderService,
            RedisStockService redisStockService) {

        this.orderService = orderService;
        this.redisStockService = redisStockService;
    }

    public Order createOrder(Integer userId, Integer categoryId) {

        boolean stockReserved =
                redisStockService.decreaseStock(categoryId);

        if (!stockReserved) {
            throw new IllegalStateException("Ticket is sold out");
        }

        try {
            return orderService.createOrder(userId, categoryId);
        } catch (RuntimeException e) {
            redisStockService.increaseStock(categoryId);
            throw e;
        }
    }
}