package com.xinyu.esportsticketing.controller;

import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.service.TicketCategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void shouldReturnCategoriesByEventId() throws Exception {

        TicketCategory standard = new TicketCategory();
        standard.setCategoryName("Standard");

        TicketCategory vip = new TicketCategory();
        vip.setCategoryName("VIP");

        when(ticketCategoryService.getCategoriesByEventId(1))
                .thenReturn(List.of(standard, vip));

        mockMvc.perform(get("/api/events/1/ticket-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Standard"))
                .andExpect(jsonPath("$[1].categoryName").value("VIP"));
    }
}