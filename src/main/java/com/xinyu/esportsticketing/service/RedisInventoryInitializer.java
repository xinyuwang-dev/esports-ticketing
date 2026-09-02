package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.repository.TicketCategoryRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.order.mode", havingValue = "redis")
public class RedisInventoryInitializer implements ApplicationRunner {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final RedisStockService redisStockService;

    public RedisInventoryInitializer(
            TicketCategoryRepository ticketCategoryRepository,
            RedisStockService redisStockService) {

        this.ticketCategoryRepository = ticketCategoryRepository;
        this.redisStockService = redisStockService;
    }

    @Override
    public void run(ApplicationArguments args) {

        for (TicketCategory category : ticketCategoryRepository.findAll()) {
            redisStockService.setStock(
                    category.getId(),
                    category.getAvailableStock()
            );
        }
    }
}
