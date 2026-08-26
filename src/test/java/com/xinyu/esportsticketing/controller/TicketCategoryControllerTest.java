package com.xinyu.esportsticketing.controller;

import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.service.DynamicPricingService;
import com.xinyu.esportsticketing.service.TicketCategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketCategoryController.class)
public class TicketCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketCategoryService ticketCategoryService;

    @MockitoBean
    private DynamicPricingService dynamicPricingService;

    @Test
    void shouldReturnCategoriesByEventId() throws Exception {

        TicketCategory standard = new TicketCategory();
        standard.setCategoryName("Standard");

        TicketCategory vip = new TicketCategory();
        vip.setCategoryName("VIP");

        when(ticketCategoryService.getCategoriesByEventId(1))
                .thenReturn(List.of(standard, vip));

        when(dynamicPricingService.calculatePrice(standard))
                .thenReturn(new BigDecimal("80.00"));

        when(dynamicPricingService.calculatePrice(vip))
                .thenReturn(new BigDecimal("300.00"));

        mockMvc.perform(get("/api/events/1/ticket-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Standard"))
                .andExpect(jsonPath("$[1].categoryName").value("VIP"))
                .andExpect(jsonPath("$[0].event").doesNotExist())
                .andExpect(jsonPath("$[0].currentPrice").value(80.00))
                .andExpect(jsonPath("$[1].currentPrice").value(300.00));
    }
}