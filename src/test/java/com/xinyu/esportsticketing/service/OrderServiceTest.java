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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Mock
    private DynamicPricingService dynamicPricingService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldCreateOrder() {

        User user = new User();

        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.setBasePrice(new BigDecimal("80.00"));

        when(userRepository.findById(1))
                .thenReturn(Optional.of(user));

        when(ticketCategoryRepository.findByIdForUpdate(3))
                .thenReturn(Optional.of(ticketCategory));

        when(dynamicPricingService.calculatePrice(ticketCategory))
                .thenReturn(new BigDecimal("120.00"));

        // Return the same order passed into save(), simulating repository persistence.
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ticketCategory.setAvailableStock(100);
        Order result = orderService.createOrder(1, 3);

        assertEquals(user, result.getUser());
        assertEquals(ticketCategory, result.getTicketCategory());
        assertEquals(new BigDecimal("120.00"), result.getFinalAmount());
        assertEquals(Order.Status.PENDING, result.getStatus());

        assertEquals(99, ticketCategory.getAvailableStock());

        verify(userRepository).findById(1);
        verify(ticketCategoryRepository).findByIdForUpdate(3);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void shouldNotCreateOrderWhenSoldOut() {

        User user = new User();

        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.setBasePrice(new BigDecimal("80.00"));
        ticketCategory.setAvailableStock(0);

        when(userRepository.findById(1))
                .thenReturn(Optional.of(user));

        when(ticketCategoryRepository.findByIdForUpdate(3))
                .thenReturn(Optional.of(ticketCategory));

        assertThrows(
                IllegalStateException.class,
                () -> orderService.createOrder(1, 3)
        );

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldReturnOrdersByUserId() {

        User user = new User();
        TicketCategory ticketCategory = new TicketCategory();

        Order order1 = new Order(
                user,
                ticketCategory,
                new BigDecimal("80.00"),
                Order.Status.PENDING
        );

        Order order2 = new Order(
                user,
                ticketCategory,
                new BigDecimal("300.00"),
                Order.Status.PENDING
        );

            List<Order> expectedOrders = List.of(order1, order2);

        when(orderRepository.findByUserId(1))
                .thenReturn(expectedOrders);

        List<Order> result =
                orderService.getOrdersByUserId(1);

        assertEquals(expectedOrders, result);

        verify(orderRepository)
                .findByUserId(1);
    }
}