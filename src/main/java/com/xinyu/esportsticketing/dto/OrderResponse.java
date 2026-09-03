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
    private String categoryName;

    public OrderResponse(
            Integer id,
            Integer userId,
            Integer categoryId,
            BigDecimal finalAmount,
            String status,
            LocalDateTime createdAt,
            String categoryName) {

        this.id = id;
        this.userId = userId;
        this.categoryId = categoryId;
        this.finalAmount = finalAmount;
        this.status = status;
        this.createdAt = createdAt;
        this.categoryName = categoryName;
    }

    public static OrderResponse fromEntity(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getTicketCategory().getId(),
                order.getFinalAmount(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getTicketCategory().getCategoryName()
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

    public String getCategoryName() { return categoryName; }
}
