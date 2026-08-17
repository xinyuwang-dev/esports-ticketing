package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.Order;
import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.entity.User;
import com.xinyu.esportsticketing.repository.OrderRepository;
import com.xinyu.esportsticketing.repository.TicketCategoryRepository;
import com.xinyu.esportsticketing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketCategoryRepository ticketCategoryRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldCreateOrder() {

        User user = new User();

        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.setBasePrice(new BigDecimal("80.00"));

        when(userRepository.findById(1))
                .thenReturn(Optional.of(user));

        when(ticketCategoryRepository.findById(3))
                .thenReturn(Optional.of(ticketCategory));

        // Return the same order passed into save(), simulating repository persistence.
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.createOrder(1, 3);

        assertEquals(user, result.getUser());
        assertEquals(ticketCategory, result.getTicketCategory());
        assertEquals(new BigDecimal("80.00"), result.getFinalAmount());
        assertEquals(Order.Status.PENDING, result.getStatus());

        verify(userRepository).findById(1);
        verify(ticketCategoryRepository).findById(3);
        verify(orderRepository).save(any(Order.class));
    }
}