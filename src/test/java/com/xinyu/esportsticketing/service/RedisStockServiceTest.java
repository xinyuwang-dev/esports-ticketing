package com.xinyu.esportsticketing.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RedisStockServiceTest {

    @Autowired
    private RedisStockService redisStockService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldPreventStockFromGoingBelowZero() {

        Integer categoryId = 999;

        redisStockService.setStock(categoryId, 1);

        boolean firstPurchase =
                redisStockService.decreaseStock(categoryId);

        boolean secondPurchase =
                redisStockService.decreaseStock(categoryId);

        assertTrue(firstPurchase);
        assertFalse(secondPurchase);

        redisTemplate.delete("ticket:stock:" + categoryId);
    }

    @Test
    void shouldRestoreStock() {

        Integer categoryId = 999;

        redisStockService.setStock(categoryId, 1);

        redisStockService.decreaseStock(categoryId);
        redisStockService.increaseStock(categoryId);

        String stock =
                redisTemplate.opsForValue().get("ticket:stock:" + categoryId);

        assertEquals("1", stock);

        redisTemplate.delete("ticket:stock:" + categoryId);
    }
}