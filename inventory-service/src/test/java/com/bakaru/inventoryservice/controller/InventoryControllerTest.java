package com.bakaru.inventoryservice.controller;

import com.bakaru.inventoryservice.dto.InventoryRequest;
import com.bakaru.inventoryservice.dto.InventoryResponse;
import com.bakaru.common.dto.ReservationLine;
import com.bakaru.inventoryservice.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    private InventoryResponse inventoryResponse;

    @BeforeEach
    void setUp() {
        inventoryResponse = InventoryResponse.builder()
                .id(1L)
                .productId(10L)
                .quantity(20)
                .reserved(0)
                .available(20)
                .build();
    }

    @Test
    void getByProductId_whenExists_returns200() throws Exception {
        when(inventoryService.getByProductId(10L)).thenReturn(inventoryResponse);

        mockMvc.perform(get("/api/inventory/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(10))
                .andExpect(jsonPath("$.quantity").value(20))
                .andExpect(jsonPath("$.available").value(20));
    }

    @Test
    void getByProductId_whenNotExists_returns404() throws Exception {
        when(inventoryService.getByProductId(99L))
                .thenThrow(new EntityNotFoundException("Inventory not found for product: 99"));

        mockMvc.perform(get("/api/inventory/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Inventory not found for product: 99"));
    }

    @Test
    void createInventory_withValidRequest_returns201() throws Exception {
        InventoryRequest request = InventoryRequest.builder()
                .productId(10L).quantity(20).build();

        when(inventoryService.createInventory(any(InventoryRequest.class))).thenReturn(inventoryResponse);

        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(10));
    }

    @Test
    void createInventory_withMissingProductId_returns400() throws Exception {
        InventoryRequest invalidRequest = InventoryRequest.builder()
                .quantity(20).build();

        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.productId").value("Product ID is required"));
    }

    @Test
    void updateStock_whenExists_returns200() throws Exception {
        when(inventoryService.updateStock(eq(10L), eq(50))).thenReturn(inventoryResponse);

        mockMvc.perform(put("/api/inventory/10")
                        .param("quantity", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(10));
    }

    @Test
    void getByProductIds_returns200WithList() throws Exception {
        when(inventoryService.getByProductIds(List.of(10L, 11L)))
                .thenReturn(List.of(inventoryResponse));

        mockMvc.perform(get("/api/inventory/batch")
                        .param("productIds", "10", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void reserve_withSufficientStock_returns200() throws Exception {
        List<ReservationLine> lines = List.of(new ReservationLine(10L, 5));
        doNothing().when(inventoryService).reserveBatch(any());

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lines)))
                .andExpect(status().isOk());

        verify(inventoryService).reserveBatch(any());
    }

    @Test
    void reserve_withInsufficientStock_returns409() throws Exception {
        List<ReservationLine> lines = List.of(new ReservationLine(10L, 5));
        doThrow(new IllegalStateException("Insufficient stock for product: 10"))
                .when(inventoryService).reserveBatch(any());

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lines)))
                .andExpect(status().isConflict());
    }

    @Test
    void reserve_withEmptyList_returns400() throws Exception {
        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());

        verify(inventoryService, never()).reserveBatch(any());
    }

    @Test
    void reserve_withNullQuantity_returns400() throws Exception {
        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"productId\": 10, \"quantity\": null}]"))
                .andExpect(status().isBadRequest());

        verify(inventoryService, never()).reserveBatch(any());
    }

    @Test
    void release_withNegativeQuantity_returns400() throws Exception {
        mockMvc.perform(post("/api/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"productId\": 10, \"quantity\": -1}]"))
                .andExpect(status().isBadRequest());

        verify(inventoryService, never()).releaseBatch(any());
    }

    @Test
    void release_returns200() throws Exception {
        List<ReservationLine> lines = List.of(new ReservationLine(10L, 5));
        doNothing().when(inventoryService).releaseBatch(any());

        mockMvc.perform(post("/api/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lines)))
                .andExpect(status().isOk());

        verify(inventoryService).releaseBatch(any());
    }
}
