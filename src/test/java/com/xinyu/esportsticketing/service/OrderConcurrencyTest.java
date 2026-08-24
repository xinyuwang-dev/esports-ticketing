package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.Event;
import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.entity.User;
import com.xinyu.esportsticketing.repository.EventRepository;
import com.xinyu.esportsticketing.repository.OrderRepository;
import com.xinyu.esportsticketing.repository.TicketCategoryRepository;
import com.xinyu.esportsticketing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
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

    @Test
    void shouldExposeOversellingProblem() throws InterruptedException {

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
        Integer categoryId = ticketCategory.getId();

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

        // Release both threads at nearly the same time.
        startLatch.countDown();

        // Wait until both purchase attempts have finished.
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
}