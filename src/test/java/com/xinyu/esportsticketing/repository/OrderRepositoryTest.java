package com.xinyu.esportsticketing.repository;

import com.xinyu.esportsticketing.entity.Order;
import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
}