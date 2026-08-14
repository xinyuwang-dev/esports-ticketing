package com.xinyu.esportsticketing.service;

import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.repository.TicketCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TicketCategoryServiceTest {

    @Mock
    private TicketCategoryRepository ticketCategoryRepository;

    @InjectMocks
    private TicketCategoryService ticketCategoryService;

    @Test
    void shouldReturnCategoriesByEventId() {

        TicketCategory standard = new TicketCategory();
        standard.setCategoryName("Standard");

        TicketCategory vip = new TicketCategory();
        vip.setCategoryName("VIP");

        List<TicketCategory> expectedCategories =
                List.of(standard, vip);

        when(ticketCategoryRepository.findByEventId(1))
                .thenReturn(expectedCategories);

        List<TicketCategory> actualCategories =
                ticketCategoryService.getCategoriesByEventId(1);

        assertEquals(expectedCategories, actualCategories);

        verify(ticketCategoryRepository).findByEventId(1);
    }
}