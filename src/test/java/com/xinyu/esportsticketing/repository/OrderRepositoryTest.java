package com.xinyu.esportsticketing.repository;

import com.xinyu.esportsticketing.entity.Order;
import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Test
    void shouldSaveOrder() {

        User user = userRepository.findById(1)
                .orElseThrow();

        TicketCategory ticketCategory = ticketCategoryRepository.findById(3)
                .orElseThrow();

        Order order = new Order(
                user,
                ticketCategory,
                new BigDecimal("80.00"),
                Order.Status.PENDING
        );

        Order savedOrder = orderRepository.save(order);

        assertNotNull(savedOrder.getId());
        assertEquals(user.getId(), savedOrder.getUser().getId());
        assertEquals(ticketCategory.getId(),
                savedOrder.getTicketCategory().getId());
        assertEquals(new BigDecimal("80.00"),
                savedOrder.getFinalAmount());
        assertEquals(Order.Status.PENDING,
                savedOrder.getStatus());
    }

    @Test
    void shouldFindOrdersByUserId() {

        User userA = userRepository.findById(1)
                .orElseThrow();

        User userB = userRepository.findById(2)
                .orElseThrow();

        TicketCategory ticketCategory = ticketCategoryRepository.findById(3)
                .orElseThrow();

        Order orderA1 = new Order(
                userA,
                ticketCategory,
                new BigDecimal("80.00"),
                Order.Status.PENDING
        );

        Order orderA2 = new Order(
                userA,
                ticketCategory,
                new BigDecimal("80.00"),
                Order.Status.PENDING
        );

        Order orderB = new Order(
                userB,
                ticketCategory,
                new BigDecimal("80.00"),
                Order.Status.PENDING
        );

        orderRepository.save(orderA1);
        orderRepository.save(orderA2);
        orderRepository.save(orderB);

        List<Order> userAOrders =
                orderRepository.findByUserId(userA.getId());

        assertTrue(
                userAOrders.stream()
                        .anyMatch(order -> order.getId().equals(orderA1.getId()))
        );

        assertTrue(
                userAOrders.stream()
                        .anyMatch(order -> order.getId().equals(orderA2.getId()))
        );

        assertFalse(
                userAOrders.stream()
                        .anyMatch(order -> order.getId().equals(orderB.getId()))
        );
    }
}