package com.bakaru.productservice.controller;

import com.bakaru.productservice.dto.ProductRequest;
import com.bakaru.productservice.dto.ProductResponse;
import com.bakaru.productservice.model.Category;
import com.bakaru.productservice.service.ProductService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private ProductResponse productResponse;
    private ProductRequest productRequest;

    @BeforeEach
    void setUp() {
        productResponse = ProductResponse.builder()
                .id(1L)
                .name("Element Deck")
                .brand("Element")
                .category(Category.DECK)
                .price(new BigDecimal("79.99"))
                .active(true)
                .build();

        productRequest = ProductRequest.builder()
                .name("Element Deck")
                .brand("Element")
                .category(Category.DECK)
                .price(new BigDecimal("79.99"))
                .build();
    }

    @Test
    void getAllProducts_returns200WithList() throws Exception {
        when(productService.getAllProducts(any(), any(), any(), any(), any()))
                .thenReturn(List.of(productResponse));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Element Deck"))
                .andExpect(jsonPath("$[0].brand").value("Element"));
    }

    @Test
    void getProductById_whenExists_returns200() throws Exception {
        when(productService.getProductById(1L)).thenReturn(productResponse);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Element Deck"));
    }

    @Test
    void getProductById_whenNotExists_returns404() throws Exception {
        when(productService.getProductById(99L))
                .thenThrow(new EntityNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void createProduct_withValidRequest_returns201() throws Exception {
        when(productService.createProduct(any(ProductRequest.class))).thenReturn(productResponse);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Element Deck"))
                .andExpect(jsonPath("$.category").value("DECK"));
    }

    @Test
    void createProduct_withMissingName_returns400() throws Exception {
        ProductRequest invalidRequest = ProductRequest.builder()
                .brand("Element")
                .category(Category.DECK)
                .price(new BigDecimal("79.99"))
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Name is required"));
    }

    @Test
    void createProduct_withNegativePrice_returns400() throws Exception {
        ProductRequest invalidRequest = ProductRequest.builder()
                .name("Element Deck")
                .brand("Element")
                .category(Category.DECK)
                .price(new BigDecimal("-10.00"))
                .build();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.price").value("Price must be positive"));
    }

    @Test
    void updateProduct_whenExists_returns200() throws Exception {
        when(productService.updateProduct(eq(1L), any(ProductRequest.class))).thenReturn(productResponse);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateProduct_whenNotExists_returns404() throws Exception {
        when(productService.updateProduct(eq(99L), any(ProductRequest.class)))
                .thenThrow(new EntityNotFoundException("Product not found with id: 99"));

        mockMvc.perform(put("/api/products/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProduct_whenExists_returns204() throws Exception {
        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(1L);
    }

    @Test
    void deleteProduct_whenNotExists_returns404() throws Exception {
        doThrow(new EntityNotFoundException("Product not found with id: 99"))
                .when(productService).deleteProduct(99L);

        mockMvc.perform(delete("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBrands_returns200WithList() throws Exception {
        when(productService.getAllBrands()).thenReturn(List.of("Element", "Santa Cruz", "Thunder"));

        mockMvc.perform(get("/api/products/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value("Element"));
    }
}
