package com.xinyu.esportsticketing.dto;

import com.xinyu.esportsticketing.entity.TicketCategory;

import java.math.BigDecimal;

public class TicketCategoryResponse {

    private Integer id;
    private String categoryName;
    private BigDecimal basePrice;
    private Integer initialStock;
    private Integer availableStock;
    private Boolean dynamicPricing;

    public TicketCategoryResponse(
            Integer id,
            String categoryName,
            BigDecimal basePrice,
            Integer initialStock,
            Integer availableStock,
            Boolean dynamicPricing) {

        this.id = id;
        this.categoryName = categoryName;
        this.basePrice = basePrice;
        this.initialStock = initialStock;
        this.availableStock = availableStock;
        this.dynamicPricing = dynamicPricing;
    }

    public Integer getId() {
        return id;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public Integer getInitialStock() {
        return initialStock;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }

    public Boolean getDynamicPricing() {
        return dynamicPricing;
    }

    public static TicketCategoryResponse fromEntity(TicketCategory category) {
        return new TicketCategoryResponse(
                category.getId(),
                category.getCategoryName(),
                category.getBasePrice(),
                category.getInitialStock(),
                category.getAvailableStock(),
                category.getDynamicPricing()
        );
    }
}