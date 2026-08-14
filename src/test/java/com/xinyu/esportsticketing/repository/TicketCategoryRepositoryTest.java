package com.xinyu.esportsticketing.repository;

import com.xinyu.esportsticketing.entity.Event;
import com.xinyu.esportsticketing.entity.TicketCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
public class TicketCategoryRepositoryTest {

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void shouldFindTicketCategoriesByEventId() {

        Event event = eventRepository.findById(1)
                .orElseThrow();

        TicketCategory standard = new TicketCategory();
        standard.setEvent(event);
        standard.setCategoryName("Standard");
        standard.setBasePrice(new BigDecimal("80.00"));
        standard.setInitialStock(100);
        standard.setAvailableStock(100);
        standard.setDynamicPricing(false);

        TicketCategory vip = new TicketCategory();
        vip.setEvent(event);
        vip.setCategoryName("VIP");
        vip.setBasePrice(new BigDecimal("300.00"));
        vip.setInitialStock(20);
        vip.setAvailableStock(20);
        vip.setDynamicPricing(true);

        ticketCategoryRepository.save(standard);
        ticketCategoryRepository.save(vip);

        List<TicketCategory> categories =
                ticketCategoryRepository.findByEventId(1);

        assertEquals(2, categories.size());
    }
}