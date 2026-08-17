package com.xinyu.esportsticketing.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "`order`") // "order" is a reserved SQL keyword
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Many orders can belong to the same user
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Many orders can reference the same ticket category
    @ManyToOne
    @JoinColumn(name = "category_id")
    private TicketCategory ticketCategory;

    // Actual amount charged for this order
    @Column(name = "final_amount")
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum Status {
        PENDING,
        PAID,
        CANCELLED
    }

    // Required by JPA
    public Order() {
    }

    public Order(User user,
                 TicketCategory ticketCategory,
                 BigDecimal finalAmount,
                 Status status) {
        this.user = user;
        this.ticketCategory = ticketCategory;
        this.finalAmount = finalAmount;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public TicketCategory getTicketCategory() {
        return ticketCategory;
    }

    public void setTicketCategory(TicketCategory ticketCategory) {
        this.ticketCategory = ticketCategory;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(BigDecimal finalAmount) {
        this.finalAmount = finalAmount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
