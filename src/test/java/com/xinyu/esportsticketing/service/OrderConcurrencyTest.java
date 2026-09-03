package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.Event;
import com.xinyu.esportsticketing.entity.Order;
import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.entity.User;
import com.xinyu.esportsticketing.repository.EventRepository;
import com.xinyu.esportsticketing.repository.OrderRepository;
import com.xinyu.esportsticketing.repository.TicketCategoryRepository;
import com.xinyu.esportsticketing.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("concurrency")
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

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
    void shouldPreventOversellingWithTwoConcurrentRequests()
            throws InterruptedException {

        User user = userRepository.findById(1)
                .orElseThrow();

        Event event = eventRepository.findById(1)
                .orElseThrow();

        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.setEvent(event);
        ticketCategory.setCategoryName("Concurrency Test");
        ticketCategory.setBasePrice(new BigDecimal("80.00"));
        ticketCategory.setInitialStock(1);
        ticketCategory.setAvailableStock(1);
        ticketCategory.setDynamicPricing(false);

        ticketCategory = ticketCategoryRepository.save(ticketCategory);
        testCategoryId = ticketCategory.getId();
        Integer categoryId = testCategoryId;

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);

        Runnable purchaseTask = () -> {
            try {
                startLatch.await();

                orderService.createOrder(1, categoryId);

                successCount.incrementAndGet();
            } catch (Exception e) {
                System.out.println("Order failed: " + e.getMessage());
            } finally {
                finishLatch.countDown();
            }
        };

        executorService.submit(purchaseTask);
        executorService.submit(purchaseTask);

        // Release both workers together.
        startLatch.countDown();

        finishLatch.await();

        executorService.shutdown();

        System.out.println("Successful orders: " + successCount.get());

        TicketCategory finalCategory =
                ticketCategoryRepository.findById(categoryId)
                        .orElseThrow();

        System.out.println(
                "Final available stock: " + finalCategory.getAvailableStock()
        );

        assertEquals(1, successCount.get());
        assertEquals(0, finalCategory.getAvailableStock());
    }

    @Test
    void shouldPreventOversellingUnderHighConcurrency()
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
        ticketCategory.setCategoryName("High Concurrency Test");
        ticketCategory.setBasePrice(new BigDecimal("80.00"));
        ticketCategory.setInitialStock(initialStock);
        ticketCategory.setAvailableStock(initialStock);
        ticketCategory.setDynamicPricing(false);

        ticketCategory = ticketCategoryRepository.save(ticketCategory);

        testCategoryId = ticketCategory.getId();
        Integer categoryId = testCategoryId;

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

                    orderService.createOrder(userId, categoryId);

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failureCount.incrementAndGet();

                } finally {
                    finishLatch.countDown();
                }
            });
        }

        long startTime = System.nanoTime();

        // Release all workers together.
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
        System.out.println("Final available stock: " + finalCategory.getAvailableStock());
        System.out.println("Duration: " + durationSeconds + " seconds");
        System.out.println("Throughput: " + throughput + " requests/second");

        assertEquals(initialStock, successCount.get());
        assertEquals(requestCount - initialStock, failureCount.get());
        assertEquals(0, finalCategory.getAvailableStock());
    }

    @AfterEach
    void cleanUpTestData() {

        if (testCategoryId != null) {

            List<Order> testOrders =
                    orderRepository.findByTicketCategoryId(testCategoryId);

            orderRepository.deleteAll(testOrders);

            ticketCategoryRepository.deleteById(testCategoryId);

            testCategoryId = null;
        }
    }


}
