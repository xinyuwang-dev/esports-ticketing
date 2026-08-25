package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.TicketCategory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class DynamicPricingService {

    public BigDecimal calculatePrice(TicketCategory ticketCategory) {

        if (!ticketCategory.getDynamicPricing()) {
            return ticketCategory.getBasePrice();
        }

        double stockRatio =
                (double) ticketCategory.getAvailableStock()
                        / ticketCategory.getInitialStock();

        if (stockRatio < 0.25) {
            return ticketCategory.getBasePrice()
                    .multiply(new BigDecimal("1.5"));
        }

        if (stockRatio < 0.50) {
            return ticketCategory.getBasePrice()
                    .multiply(new BigDecimal("1.2"));
        }

        return ticketCategory.getBasePrice();
    }
}