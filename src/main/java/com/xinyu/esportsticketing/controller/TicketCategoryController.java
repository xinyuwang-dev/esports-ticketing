package com.xinyu.esportsticketing.controller;

import com.xinyu.esportsticketing.dto.TicketCategoryResponse;
import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.service.DynamicPricingService;
import com.xinyu.esportsticketing.service.TicketCategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class TicketCategoryController {

    private final TicketCategoryService ticketCategoryService;
    private final DynamicPricingService dynamicPricingService;

    public TicketCategoryController(TicketCategoryService ticketCategoryService,
                                    DynamicPricingService dynamicPricingService) {

        this.ticketCategoryService = ticketCategoryService;
        this.dynamicPricingService = dynamicPricingService;
    }

    @GetMapping("/{eventId}/ticket-categories")
    public List<TicketCategoryResponse> getCategoriesByEventId(
            @PathVariable Integer eventId) {

        return ticketCategoryService.getCategoriesByEventId(eventId)
                .stream()
                .map(category -> TicketCategoryResponse.fromEntity(
                        category,
                        dynamicPricingService.calculatePrice(category)
                ))
                .toList();
    }
}