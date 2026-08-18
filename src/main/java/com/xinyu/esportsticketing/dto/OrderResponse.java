package com.xinyu.esportsticketing.dto;

import com.xinyu.esportsticketing.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderResponse {

    private Integer id;
    private Integer userId;
    private Integer categoryId;
    private BigDecimal finalAmount;
    private String status;
    private LocalDateTime createdAt;

    public OrderResponse(
            Integer id,
            Integer userId,
            Integer categoryId,
            BigDecimal finalAmount,
            String status,
            LocalDateTime createdAt) {

        this.id = id;
        this.userId = userId;
        this.categoryId = categoryId;
        this.finalAmount = finalAmount;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Convert the JPA entity into a clean API response.
    public static OrderResponse fromEntity(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getTicketCategory().getId(),
                order.getFinalAmount(),
                order.getStatus().name(),
                order.getCreatedAt()
        );
    }

    public Integer getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}