package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.TicketCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicPricingServiceTest {

    private final DynamicPricingService dynamicPricingService =
            new DynamicPricingService();

    @Test
    void shouldReturnBasePriceWhenDynamicPricingIsDisabled() {

        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.setBasePrice(new BigDecimal("80.00"));
        ticketCategory.setInitialStock(100);
        ticketCategory.setAvailableStock(10);
        ticketCategory.setDynamicPricing(false);

        BigDecimal result =
                dynamicPricingService.calculatePrice(ticketCategory);

        assertEquals(new BigDecimal("80.00"), result);
    }

    @Test
    void shouldIncreasePriceByFiftyPercentWhenStockIsBelowTwentyFivePercent() {

        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.setBasePrice(new BigDecimal("80.00"));
        ticketCategory.setInitialStock(100);
        ticketCategory.setAvailableStock(20);
        ticketCategory.setDynamicPricing(true);

        BigDecimal result =
                dynamicPricingService.calculatePrice(ticketCategory);

        assertEquals(new BigDecimal("120.000"), result);
    }

    @Test
    void shouldIncreasePriceByTwentyPercentWhenStockIsBelowFiftyPercent() {

        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.setBasePrice(new BigDecimal("80.00"));
        ticketCategory.setInitialStock(100);
        ticketCategory.setAvailableStock(40);
        ticketCategory.setDynamicPricing(true);

        BigDecimal result =
                dynamicPricingService.calculatePrice(ticketCategory);

        assertEquals(new BigDecimal("96.000"), result);
    }

    @Test
    void shouldReturnBasePriceWhenStockIsAtLeastFiftyPercent() {

        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.setBasePrice(new BigDecimal("80.00"));
        ticketCategory.setInitialStock(100);
        ticketCategory.setAvailableStock(60);
        ticketCategory.setDynamicPricing(true);

        BigDecimal result =
                dynamicPricingService.calculatePrice(ticketCategory);

        assertEquals(new BigDecimal("80.00"), result);
    }
}