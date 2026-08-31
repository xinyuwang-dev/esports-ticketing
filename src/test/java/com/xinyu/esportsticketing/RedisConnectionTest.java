package com.xinyu.esportsticketing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldConnectToRedis() {

        redisTemplate.opsForValue().set("test:connection", "hello");

        String value =
                redisTemplate.opsForValue().get("test:connection");

        assertEquals("hello", value);

        redisTemplate.delete("test:connection");
    }

    @Test
    void shouldNotDecreaseStockBelowZero() {

        String key = "test:ticket:stock";
        redisTemplate.opsForValue().set(key, "1");

        String script = """
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if stock and stock > 0 then
                return redis.call('DECR', KEYS[1])
            end
            return -1
            """;

        DefaultRedisScript<Long> redisScript =
                new DefaultRedisScript<>(script, Long.class);

        Long first = redisTemplate.execute(redisScript, List.of(key));
        Long second = redisTemplate.execute(redisScript, List.of(key));

        assertEquals(0L, first);
        assertEquals(-1L, second);
        assertEquals("0", redisTemplate.opsForValue().get(key));

        redisTemplate.delete(key);
    }
}