package com.xinyu.esportsticketing.controller;

import com.xinyu.esportsticketing.dto.CreateOrderRequest;
import com.xinyu.esportsticketing.dto.OrderResponse;
import com.xinyu.esportsticketing.entity.Order;
import com.xinyu.esportsticketing.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse createOrder(@RequestBody CreateOrderRequest request) {

        Order order = orderService.createOrder(
                request.getUserId(),
                request.getCategoryId()
        );

        return OrderResponse.fromEntity(order);
    }

    @GetMapping("/user/{userId}")
    public List<OrderResponse> getOrdersByUserId(
            @PathVariable Integer userId) {

        return orderService.getOrdersByUserId(userId)
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }
}