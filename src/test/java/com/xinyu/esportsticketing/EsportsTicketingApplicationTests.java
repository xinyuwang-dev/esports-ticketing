package com.xinyu.esportsticketing;

import com.xinyu.esportsticketing.service.RedisInventoryInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.order.mode=mysql",
        "spring.data.redis.port=1"
})
class EsportsTicketingApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertTrue(
                applicationContext
                        .getBeansOfType(RedisInventoryInitializer.class)
                        .isEmpty()
        );
    }

}
