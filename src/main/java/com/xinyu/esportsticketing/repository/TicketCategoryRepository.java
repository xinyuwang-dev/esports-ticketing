package com.xinyu.esportsticketing.repository;

import com.xinyu.esportsticketing.entity.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCategoryRepository
        extends JpaRepository<TicketCategory, Integer> {

    List<TicketCategory> findByEventId(Integer eventId);
}