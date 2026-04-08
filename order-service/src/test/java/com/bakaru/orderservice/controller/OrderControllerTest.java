package com.bakaru.orderservice.controller;

import com.bakaru.orderservice.dto.OrderItemRequest;
import com.bakaru.orderservice.dto.OrderRequest;
import com.bakaru.orderservice.dto.OrderResponse;
import com.bakaru.orderservice.model.OrderStatus;
import com.bakaru.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private OrderResponse orderResponse;
    private OrderRequest orderRequest;

    @BeforeEach
    void setUp() {
        orderResponse = OrderResponse.builder()
                .id(1L)
                .customerId(100L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("159.98"))
                .createdAt(LocalDateTime.now())
                .items(List.of())
                .build();

        orderRequest = OrderRequest.builder()
                .customerId(100L)
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L)
                        .quantity(2)
                        .unitPrice(new BigDecimal("79.99"))
                        .build()))
                .build();
    }

    @Test
    void createOrder_withValidRequest_returns201() throws Exception {
        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(orderResponse);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(100))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createOrder_withMissingCustomerId_returns400() throws Exception {
        OrderRequest invalidRequest = OrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L).quantity(1).unitPrice(new BigDecimal("79.99")).build()))
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.customerId").value("Customer ID is required"));
    }

    @Test
    void createOrder_withEmptyItems_returns400() throws Exception {
        OrderRequest invalidRequest = OrderRequest.builder()
                .customerId(100L)
                .items(List.of())
                .build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.items").value("Order must have at least one item"));
    }

    @Test
    void getOrderById_whenExists_returns200() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(orderResponse);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customerId").value(100));
    }

    @Test
    void getOrderById_whenNotExists_returns404() throws Exception {
        when(orderService.getOrderById(99L))
                .thenThrow(new EntityNotFoundException("Order not found with id: 99"));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found with id: 99"));
    }

    @Test
    void getOrdersByCustomer_returns200WithList() throws Exception {
        when(orderService.getOrdersByCustomer(100L)).thenReturn(List.of(orderResponse));

        mockMvc.perform(get("/api/orders/customer/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerId").value(100));
    }

    @Test
    void updateStatus_whenExists_returns200() throws Exception {
        OrderResponse paidResponse = OrderResponse.builder()
                .id(1L).customerId(100L).status(OrderStatus.PAID).build();

        when(orderService.updateStatus(1L, OrderStatus.PAID)).thenReturn(paidResponse);

        mockMvc.perform(patch("/api/orders/1/status")
                        .param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void updateStatus_whenNotExists_returns404() throws Exception {
        when(orderService.updateStatus(eq(99L), any(OrderStatus.class)))
                .thenThrow(new EntityNotFoundException("Order not found with id: 99"));

        mockMvc.perform(patch("/api/orders/99/status")
                        .param("status", "PAID"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrder_whenExists_returns204() throws Exception {
        doNothing().when(orderService).cancelOrder(1L);

        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isNoContent());

        verify(orderService).cancelOrder(1L);
    }

    @Test
    void cancelOrder_whenNotExists_returns404() throws Exception {
        doThrow(new EntityNotFoundException("Order not found with id: 99"))
                .when(orderService).cancelOrder(99L);

        mockMvc.perform(delete("/api/orders/99"))
                .andExpect(status().isNotFound());
    }
}
