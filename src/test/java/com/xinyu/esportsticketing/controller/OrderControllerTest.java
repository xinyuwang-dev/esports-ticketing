package com.xinyu.esportsticketing.controller;

import com.xinyu.esportsticketing.entity.Order;
import com.xinyu.esportsticketing.entity.TicketCategory;
import com.xinyu.esportsticketing.entity.User;
import com.xinyu.esportsticketing.service.OrderPlacementService;
import com.xinyu.esportsticketing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderPlacementService orderPlacementService;

    @MockitoBean
    private OrderService orderService;

    @Test
    void shouldCreateOrder() throws Exception {

        User user = new User();
        user.setId(1);

        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.setId(3);

        Order order = new Order(
                user,
                ticketCategory,
                new BigDecimal("80.00"),
                Order.Status.PENDING
        );
        order.setId(10);

        when(orderPlacementService.createOrder(1, 3))
                .thenReturn(order);

        mockMvc.perform(post("/api/orders")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": 1,
                                  "categoryId": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.categoryId").value(3))
                .andExpect(jsonPath("$.finalAmount").value(80.0))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.ticketCategory").doesNotExist());
    }

    @Test
    void shouldReturnOrdersByUserId() throws Exception {

        User user = new User();
        user.setId(6);

        TicketCategory ticketCategory = new TicketCategory();
        ticketCategory.setId(3);
        ticketCategory.setCategoryName("Standard");

        Order order = new Order(
                user,
                ticketCategory,
                new BigDecimal("80.00"),
                Order.Status.PENDING
        );
        order.setId(100);

        when(orderService.getOrdersByUserId(6))
                .thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders/user/6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].userId").value(6))
                .andExpect(jsonPath("$[0].categoryId").value(3))
                .andExpect(jsonPath("$[0].finalAmount").value(80.00))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].categoryName").value("Standard"));
    }
}
