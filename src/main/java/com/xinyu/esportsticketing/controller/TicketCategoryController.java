package com.xinyu.esportsticketing.controller;

import com.xinyu.esportsticketing.entity.TicketCategory;
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

    public TicketCategoryController(TicketCategoryService ticketCategoryService) {
        this.ticketCategoryService = ticketCategoryService;
    }

    @GetMapping("/{eventId}/ticket-categories")
    public List<TicketCategory> getCategoriesByEventId(
            @PathVariable Integer eventId) {

        return ticketCategoryService.getCategoriesByEventId(eventId);
    }
}