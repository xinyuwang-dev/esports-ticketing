package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.Order;
import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.entity.User;
import com.xinyu.esportsticketing.repository.OrderRepository;
import com.xinyu.esportsticketing.repository.TicketCategoryRepository;
import com.xinyu.esportsticketing.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    // Constructor injection keeps dependencies explicit and easy to test.
    public OrderService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            TicketCategoryRepository ticketCategoryRepository) {

        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
    }

    @Transactional
    public Order createOrder(Integer userId, Integer categoryId) {

        // Load trusted domain data from the database instead of relying on client input.
        User user = userRepository.findById(userId)
                .orElseThrow();

        TicketCategory ticketCategory =
                ticketCategoryRepository.findByIdForUpdate(categoryId)
                        .orElseThrow();

        // Check that the selected ticket category still has available stock.
        if (ticketCategory.getAvailableStock() <= 0) {
            throw new IllegalStateException("Ticket is sold out");
        }

        // One successful order currently represents one ticket purchase.
        ticketCategory.setAvailableStock(
                ticketCategory.getAvailableStock() - 1
        );

        ticketCategoryRepository.save(ticketCategory);

        // The server determines the order price from the ticket category.
        // The client is not allowed to provide or override the final amount.
        Order order = new Order(
                user,
                ticketCategory,
                ticketCategory.getBasePrice(),
                Order.Status.PENDING
        );

        // A newly created order starts as PENDING and is persisted to the database.
        return orderRepository.save(order);
    }
}