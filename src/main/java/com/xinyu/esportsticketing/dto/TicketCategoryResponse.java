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
    private BigDecimal currentPrice;

    public TicketCategoryResponse(
            Integer id,
            String categoryName,
            BigDecimal basePrice,
            Integer initialStock,
            Integer availableStock,
            Boolean dynamicPricing,
            BigDecimal currentPrice) {

        this.id = id;
        this.categoryName = categoryName;
        this.basePrice = basePrice;
        this.initialStock = initialStock;
        this.availableStock = availableStock;
        this.dynamicPricing = dynamicPricing;
        this.currentPrice = currentPrice;
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

    public BigDecimal getCurrentPrice() {   return currentPrice; }

    public static TicketCategoryResponse fromEntity(
            TicketCategory category,
            BigDecimal currentPrice) {
        return new TicketCategoryResponse(
                category.getId(),
                category.getCategoryName(),
                category.getBasePrice(),
                category.getInitialStock(),
                category.getAvailableStock(),
                category.getDynamicPricing(),
                currentPrice
        );
    }
}