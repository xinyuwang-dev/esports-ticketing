package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.repository.EventRepository;
import com.xinyu.esportsticketing.repository.OrderRepository;
import com.xinyu.esportsticketing.repository.TicketCategoryRepository;
import com.xinyu.esportsticketing.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xinyu.esportsticketing.entity.Event;
import com.xinyu.esportsticketing.entity.TicketCategory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("concurrency")
class RedisOrderConcurrencyTest {

    @Autowired
    private RedisOrderService redisOrderService;

    @Autowired
    private RedisStockService redisStockService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    private Integer testCategoryId;

    @Test
    void shouldPreventOversellingWithRedis()
            throws InterruptedException {

        int initialStock = 100;
        int requestCount = 5000;
        int threadCount = 100;

        Integer userId = userRepository.findById(1)
                .orElseThrow()
                .getId();

        Event event = eventRepository.findById(1)
                .orElseThrow();

        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.setEvent(event);
        ticketCategory.setCategoryName("Redis Concurrency Test");
        ticketCategory.setBasePrice(new BigDecimal("80.00"));
        ticketCategory.setInitialStock(initialStock);
        ticketCategory.setAvailableStock(initialStock);
        ticketCategory.setDynamicPricing(false);

        ticketCategory = ticketCategoryRepository.save(ticketCategory);

        testCategoryId = ticketCategory.getId();
        Integer categoryId = testCategoryId;

        redisStockService.setStock(categoryId, initialStock);

        ExecutorService executorService =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    redisOrderService.createOrder(userId, categoryId);

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        long startTime = System.nanoTime();

        startLatch.countDown();
        finishLatch.await();

        long endTime = System.nanoTime();

        executorService.shutdown();

        double durationSeconds =
                (endTime - startTime) / 1_000_000_000.0;

        double throughput =
                requestCount / durationSeconds;

        TicketCategory finalCategory =
                ticketCategoryRepository.findById(categoryId)
                        .orElseThrow();

        System.out.println("Initial stock: " + initialStock);
        System.out.println("Total requests: " + requestCount);
        System.out.println("Concurrent threads: " + threadCount);
        System.out.println("Successful orders: " + successCount.get());
        System.out.println("Failed orders: " + failureCount.get());
        System.out.println("Final MySQL stock: " + finalCategory.getAvailableStock());
        System.out.println("Duration: " + durationSeconds + " seconds");
        System.out.println("Throughput: " + throughput + " requests/second");

        assertEquals(initialStock, successCount.get());
        assertEquals(requestCount - initialStock, failureCount.get());
        assertEquals(0, finalCategory.getAvailableStock());
    }

    @AfterEach
    void cleanUpTestData() {
        if (testCategoryId != null) {

            orderRepository.deleteAll(
                    orderRepository.findByTicketCategoryId(testCategoryId)
            );

            ticketCategoryRepository.deleteById(testCategoryId);

            redisStockService.deleteStock(testCategoryId);

            testCategoryId = null;
        }
    }
}