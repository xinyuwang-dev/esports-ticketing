package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.repository.TicketCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TicketCategoryService {

    private final TicketCategoryRepository ticketCategoryRepository;

    public TicketCategoryService(TicketCategoryRepository ticketCategoryRepository) {
        this.ticketCategoryRepository = ticketCategoryRepository;
    }

    public List<TicketCategory> getCategoriesByEventId(Integer eventId) {
        return ticketCategoryRepository.findByEventId(eventId);
    }
}