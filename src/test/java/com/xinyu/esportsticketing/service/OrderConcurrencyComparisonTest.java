package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.Event;
import com.xinyu.esportsticketing.entity.Order;
import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.entity.User;
import com.xinyu.esportsticketing.repository.EventRepository;
import com.xinyu.esportsticketing.repository.OrderRepository;
import com.xinyu.esportsticketing.repository.TicketCategoryRepository;
import com.xinyu.esportsticketing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("concurrency")
class OrderConcurrencyComparisonTest {

    private static final int INITIAL_STOCK = 100;
    private static final int REQUEST_COUNT = 5000;
    private static final int THREAD_COUNT = 100;
    private static final int HIKARI_MAXIMUM_POOL_SIZE = 10;
    private static final int MEASURED_RUNS = 6;

    private static final int READY_TIMEOUT_SECONDS = 30;
    private static final int FINISH_TIMEOUT_SECONDS = 180;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisOrderService redisOrderService;

    @Autowired
    private RedisStockService redisStockService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    private boolean safeToCleanSharedData = true;

    @Test
    void shouldCompareMysqlAndRedisConcurrency()
            throws InterruptedException {

        safeToCleanSharedData = true;

        User testUser = null;
        Event testEvent = null;

        try {
            String suffix = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12);

            testUser = createTestUser(suffix);
            testEvent = createTestEvent(suffix);

            System.out.printf(
                    "EXPERIMENT_DATA,userId=%d,eventId=%d%n",
                    testUser.getId(),
                    testEvent.getId()
            );

            Integer userId = testUser.getId();

            // Warm up both paths. These results are not reported.
            runScenario(
                    "mysql",
                    0,
                    userId,
                    testEvent,
                    orderService::createOrder,
                    false
            );

            runScenario(
                    "redis",
                    0,
                    userId,
                    testEvent,
                    redisOrderService::createOrder,
                    true
            );

            System.out.println(
                    "EXPERIMENT_RESULT,mode,run,initialStock,requestCount,"
                            + "threadCount,hikariMaximumPoolSize,successes,"
                            + "soldOutRejections,unexpectedFailures,"
                            + "finalMysqlStock,finalRedisStock,orders,"
                            + "totalDurationSeconds,totalAttemptsPerSecond,"
                            + "successfulOrdersPerSecond"
            );

            for (int run = 1; run <= MEASURED_RUNS; run++) {
                if (run % 2 == 1) {
                    printResult(runScenario(
                            "mysql",
                            run,
                            userId,
                            testEvent,
                            orderService::createOrder,
                            false
                    ));

                    printResult(runScenario(
                            "redis",
                            run,
                            userId,
                            testEvent,
                            redisOrderService::createOrder,
                            true
                    ));
                } else {
                    printResult(runScenario(
                            "redis",
                            run,
                            userId,
                            testEvent,
                            redisOrderService::createOrder,
                            true
                    ));

                    printResult(runScenario(
                            "mysql",
                            run,
                            userId,
                            testEvent,
                            orderService::createOrder,
                            false
                    ));
                }
            }
        } finally {
            if (safeToCleanSharedData) {
                if (testEvent != null) {
                    eventRepository.deleteById(testEvent.getId());
                }

                if (testUser != null) {
                    userRepository.deleteById(testUser.getId());
                }
            }
        }
    }

    private User createTestUser(String suffix) {
        User user = new User();
        user.setUsername("comparison_" + suffix);
        user.setEmail(
                "comparison_" + suffix + "@example.com"
        );
        user.setPasswordHash("comparison-test-not-used");

        return userRepository.saveAndFlush(user);
    }

    private Event createTestEvent(String suffix) {
        Event event = new Event();
        event.setTitle("Concurrency Comparison " + suffix);
        event.setStartTime(LocalDateTime.now().plusDays(1));
        event.setStatus("UPCOMING");
        event.setCreatedAt(LocalDateTime.now());

        return eventRepository.saveAndFlush(event);
    }

    private ExperimentResult runScenario(
            String mode,
            int run,
            Integer userId,
            Event event,
            BiFunction<Integer, Integer, Order> orderCreator,
            boolean redis)
            throws InterruptedException {

        TicketCategory category = new TicketCategory();
        category.setEvent(event);
        category.setCategoryName(
                "Comparison " + mode + " run " + run
        );
        category.setBasePrice(new BigDecimal("80.00"));
        category.setInitialStock(INITIAL_STOCK);
        category.setAvailableStock(INITIAL_STOCK);
        category.setDynamicPricing(false);

        category = ticketCategoryRepository.saveAndFlush(category);
        Integer categoryId = category.getId();

        System.out.printf(
                "EXPERIMENT_CATEGORY,mode=%s,run=%d,categoryId=%d%n",
                mode,
                run,
                categoryId
        );

        ExecutorService executorService =
                Executors.newFixedThreadPool(THREAD_COUNT);

        CountDownLatch readyLatch =
                new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch =
                new CountDownLatch(REQUEST_COUNT);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger soldOutRejectionCount =
                new AtomicInteger();
        AtomicInteger unexpectedFailureCount =
                new AtomicInteger();

        AtomicReference<Throwable> firstUnexpectedFailure =
                new AtomicReference<>();

        try {
            if (redis) {
                redisStockService.setStock(
                        categoryId,
                        INITIAL_STOCK
                );
            }

            for (int i = 0; i < REQUEST_COUNT; i++) {
                executorService.submit(() -> {
                    readyLatch.countDown();

                    try {
                        startLatch.await();

                        orderCreator.apply(userId, categoryId);

                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();

                        unexpectedFailureCount.incrementAndGet();
                        firstUnexpectedFailure.compareAndSet(
                                null,
                                e
                        );
                    } catch (IllegalStateException e) {
                        if ("Ticket is sold out"
                                .equals(e.getMessage())) {

                            soldOutRejectionCount
                                    .incrementAndGet();
                        } else {
                            unexpectedFailureCount
                                    .incrementAndGet();

                            firstUnexpectedFailure.compareAndSet(
                                    null,
                                    e
                            );
                        }
                    } catch (RuntimeException e) {
                        unexpectedFailureCount.incrementAndGet();

                        firstUnexpectedFailure.compareAndSet(
                                null,
                                e
                        );
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            boolean workersReady = readyLatch.await(
                    READY_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            assertTrue(
                    workersReady,
                    "Timed out waiting for worker threads "
                            + "to become ready"
            );

            long startTime = System.nanoTime();

            startLatch.countDown();

            boolean requestsFinished = finishLatch.await(
                    FINISH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            long endTime = System.nanoTime();

            assertTrue(
                    requestsFinished,
                    "Timed out waiting for purchase requests "
                            + "to finish"
            );

            TicketCategory finalCategory =
                    ticketCategoryRepository.findById(categoryId)
                            .orElseThrow();

            int orderCount =
                    orderRepository
                            .findByTicketCategoryId(categoryId)
                            .size();

            Integer finalRedisStock = null;

            if (redis) {
                String redisValue =
                        redisTemplate.opsForValue().get(
                                "ticket:stock:" + categoryId
                        );

                if (redisValue != null) {
                    finalRedisStock =
                            Integer.valueOf(redisValue);
                }
            }

            Throwable firstFailure =
                    firstUnexpectedFailure.get();

            String firstFailureDescription =
                    describeFailure(firstFailure);

            if (firstFailure != null) {
                System.err.printf(
                        "FIRST_UNEXPECTED_FAILURE "
                                + "mode=%s run=%d type=%s "
                                + "message=%s%n",
                        mode,
                        run,
                        firstFailure.getClass().getName(),
                        sanitiseMessage(firstFailure.getMessage())
                );
            }

            int completedRequestCount =
                    successCount.get()
                            + soldOutRejectionCount.get()
                            + unexpectedFailureCount.get();

            assertEquals(
                    REQUEST_COUNT,
                    completedRequestCount,
                    "Every submitted request must have "
                            + "exactly one outcome"
            );

            assertEquals(
                    INITIAL_STOCK,
                    successCount.get()
            );

            assertEquals(
                    REQUEST_COUNT - INITIAL_STOCK,
                    soldOutRejectionCount.get()
            );

            assertEquals(
                    0,
                    unexpectedFailureCount.get(),
                    "First unexpected failure: "
                            + firstFailureDescription
            );

            assertEquals(
                    0,
                    finalCategory.getAvailableStock()
            );

            assertEquals(
                    INITIAL_STOCK,
                    orderCount
            );

            if (redis) {
                assertEquals(
                        Integer.valueOf(0),
                        finalRedisStock
                );
            }

            double durationSeconds =
                    (endTime - startTime)
                            / 1_000_000_000.0;

            return new ExperimentResult(
                    mode,
                    run,
                    successCount.get(),
                    soldOutRejectionCount.get(),
                    unexpectedFailureCount.get(),
                    finalCategory.getAvailableStock(),
                    finalRedisStock,
                    orderCount,
                    durationSeconds,
                    REQUEST_COUNT / durationSeconds,
                    successCount.get() / durationSeconds
            );
        } finally {
            // Stop all workers before touching their database data.
            executorService.shutdownNow();

            boolean workersStopped;

            try {
                workersStopped =
                        executorService.awaitTermination(
                                SHUTDOWN_TIMEOUT_SECONDS,
                                TimeUnit.SECONDS
                        );
            } catch (InterruptedException e) {
                safeToCleanSharedData = false;
                Thread.currentThread().interrupt();
                throw e;
            }

            if (!workersStopped) {
                safeToCleanSharedData = false;

                throw new AssertionError(
                        "Worker threads for categoryId "
                                + categoryId
                                + " did not stop within "
                                + SHUTDOWN_TIMEOUT_SECONDS
                                + " seconds; cleanup was skipped "
                                + "to avoid racing active requests"
                );
            }

            orderRepository.deleteAll(
                    orderRepository.findByTicketCategoryId(
                            categoryId
                    )
            );

            ticketCategoryRepository.deleteById(categoryId);

            if (redis) {
                redisStockService.deleteStock(categoryId);
            }
        }
    }

    private String describeFailure(Throwable failure) {
        if (failure == null) {
            return "none recorded";
        }

        return failure.getClass().getName()
                + ": "
                + sanitiseMessage(failure.getMessage());
    }

    private String sanitiseMessage(String message) {
        if (message == null) {
            return "<no message>";
        }

        return message
                .replace('\r', ' ')
                .replace('\n', ' ');
    }

    private void printResult(ExperimentResult result) {
        String redisStock =
                result.redisStock() == null
                        ? "NA"
                        : result.redisStock().toString();

        System.out.printf(
                Locale.ROOT,
                "EXPERIMENT_RESULT,%s,%d,%d,%d,%d,%d,"
                        + "%d,%d,%d,%d,%s,%d,"
                        + "%.6f,%.2f,%.2f%n",
                result.mode(),
                result.run(),
                INITIAL_STOCK,
                REQUEST_COUNT,
                THREAD_COUNT,
                HIKARI_MAXIMUM_POOL_SIZE,
                result.successCount(),
                result.soldOutRejectionCount(),
                result.unexpectedFailureCount(),
                result.mysqlStock(),
                redisStock,
                result.orderCount(),
                result.durationSeconds(),
                result.attemptsPerSecond(),
                result.successfulOrdersPerSecond()
        );
    }

    private record ExperimentResult(
            String mode,
            int run,
            int successCount,
            int soldOutRejectionCount,
            int unexpectedFailureCount,
            int mysqlStock,
            Integer redisStock,
            int orderCount,
            double durationSeconds,
            double attemptsPerSecond,
            double successfulOrdersPerSecond) {
    }
}
