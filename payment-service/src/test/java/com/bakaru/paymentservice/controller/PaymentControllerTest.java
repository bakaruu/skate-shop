package com.bakaru.paymentservice.controller;

import com.bakaru.paymentservice.dto.CheckoutResponse;
import com.bakaru.paymentservice.dto.PaymentRequest;
import com.bakaru.paymentservice.dto.PaymentResponse;
import com.bakaru.paymentservice.model.PaymentStatus;
import com.bakaru.paymentservice.service.PaymentService;
import com.bakaru.paymentservice.service.WebhookService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private WebhookService webhookService;

    private PaymentRequest paymentRequest;
    private CheckoutResponse checkoutResponse;
    private PaymentResponse paymentResponse;

    @BeforeEach
    void setUp() {
        paymentRequest = PaymentRequest.builder()
                .orderId(10L)
                .customerId(100L)
                .amount(new BigDecimal("79.99"))
                .build();

        checkoutResponse = CheckoutResponse.builder()
                .checkoutUrl("https://checkout.stripe.com/pay/sess_123")
                .sessionId("sess_123")
                .orderId(10L)
                .build();

        paymentResponse = PaymentResponse.builder()
                .id(1L)
                .orderId(10L)
                .customerId(100L)
                .amount(new BigDecimal("79.99"))
                .status(PaymentStatus.COMPLETED)
                .stripeSessionId("sess_123")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createCheckout_withValidRequest_returns200() throws Exception {
        when(paymentService.createCheckoutSession(any(PaymentRequest.class)))
                .thenReturn(checkoutResponse);

        mockMvc.perform(post("/api/payments/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess_123"))
                .andExpect(jsonPath("$.orderId").value(10));
    }

    @Test
    void createCheckout_withMissingOrderId_returns400() throws Exception {
        PaymentRequest invalidRequest = PaymentRequest.builder()
                .customerId(100L)
                .amount(new BigDecimal("79.99"))
                .build();

        mockMvc.perform(post("/api/payments/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.orderId").value("Order ID is required"));
    }

    @Test
    void createCheckout_withNegativeAmount_returns400() throws Exception {
        PaymentRequest invalidRequest = PaymentRequest.builder()
                .orderId(10L)
                .customerId(100L)
                .amount(new BigDecimal("-10.00"))
                .build();

        mockMvc.perform(post("/api/payments/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount").value("Amount must be positive"));
    }

    @Test
    void handleWebhook_withValidSignature_returns200() throws Exception {
        doNothing().when(webhookService).processWebhook(any(), any());

        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("Stripe-Signature", "t=123,v1=abc")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(webhookService).processWebhook(any(), eq("t=123,v1=abc"));
    }

    @Test
    void getByOrderId_whenExists_returns200() throws Exception {
        when(paymentService.getPaymentByOrderId(10L)).thenReturn(paymentResponse);

        mockMvc.perform(get("/api/payments/order/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(10))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getByOrderId_whenNotExists_returns404() throws Exception {
        when(paymentService.getPaymentByOrderId(99L))
                .thenThrow(new EntityNotFoundException("Payment not found for order: 99"));

        mockMvc.perform(get("/api/payments/order/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Payment not found for order: 99"));
    }

    @Test
    void getByCustomer_returns200WithList() throws Exception {
        when(paymentService.getPaymentsByCustomer(100L)).thenReturn(List.of(paymentResponse));

        mockMvc.perform(get("/api/payments/customer/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerId").value(100));
    }
}
