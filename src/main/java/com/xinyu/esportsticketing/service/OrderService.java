package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.Order;
import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.entity.User;
import com.xinyu.esportsticketing.repository.OrderRepository;
import com.xinyu.esportsticketing.repository.TicketCategoryRepository;
import com.xinyu.esportsticketing.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final DynamicPricingService dynamicPricingService;

    public OrderService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            TicketCategoryRepository ticketCategoryRepository,
            DynamicPricingService dynamicPricingService) {

        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.dynamicPricingService = dynamicPricingService;
    }

    public List<Order> getOrdersByUserId(Integer userId) {
        return orderRepository.findByUserId(userId);
    }

    @Transactional
    public Order createOrder(Integer userId, Integer categoryId) {

        User user = userRepository.findById(userId)
                .orElseThrow();

        TicketCategory ticketCategory =
                ticketCategoryRepository.findByIdForUpdate(categoryId)
                        .orElseThrow();

        if (ticketCategory.getAvailableStock() <= 0) {
            throw new IllegalStateException("Ticket is sold out");
        }

        BigDecimal finalAmount =
                dynamicPricingService.calculatePrice(ticketCategory);

        // Each order is for one ticket.
        ticketCategory.setAvailableStock(
                ticketCategory.getAvailableStock() - 1
        );

        ticketCategoryRepository.save(ticketCategory);

        // Save the price calculated by the server, not a value from the request.
        Order order = new Order(
                user,
                ticketCategory,
                finalAmount,
                Order.Status.PENDING
        );

        return orderRepository.save(order);
    }
}
