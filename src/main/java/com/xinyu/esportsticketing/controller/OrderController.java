package com.xinyu.esportsticketing.controller;

import com.xinyu.esportsticketing.dto.CreateOrderRequest;
import com.xinyu.esportsticketing.dto.OrderResponse;
import com.xinyu.esportsticketing.entity.Order;
import com.xinyu.esportsticketing.service.OrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}