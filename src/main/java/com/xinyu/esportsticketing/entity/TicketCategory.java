package com.xinyu.esportsticketing.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * TicketCategory Entity - Maps to the 'ticket_category' table.
 * Crucial for the dynamic pricing feature.
 */
@Data
@Entity
@Table(name = "ticket_category")
public class TicketCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Many-to-One relationship back to Event
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "initial_stock", nullable = false)
    private Integer initialStock;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    // The switch for our dynamic pricing feature!
    @Column(name = "is_dynamic_pricing", nullable = false)
    private Boolean dynamicPricing;
}