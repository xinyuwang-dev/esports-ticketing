package com.xinyu.esportsticketing.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RedisStockService {

    private final StringRedisTemplate redisTemplate;

    public RedisStockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setStock(Integer categoryId, int stock) {
        String key = "ticket:stock:" + categoryId;
        redisTemplate.opsForValue().set(key, String.valueOf(stock));
    }

    public boolean decreaseStock(Integer categoryId) {
        String key = "ticket:stock:" + categoryId;

        String script = """
                local stock = tonumber(redis.call('GET', KEYS[1]))
                if stock and stock > 0 then
                    redis.call('DECR', KEYS[1])
                    return 1
                end
                return 0
                """;

        DefaultRedisScript<Long> redisScript =
                new DefaultRedisScript<>(script, Long.class);

        Long result =
                redisTemplate.execute(redisScript, List.of(key));

        return result != null && result == 1;
    }

    public void increaseStock(Integer categoryId) {
        String key = "ticket:stock:" + categoryId;
        redisTemplate.opsForValue().increment(key);
    }

    public void deleteStock(Integer categoryId) {
        String key = "ticket:stock:" + categoryId;
        redisTemplate.delete(key);
    }
}