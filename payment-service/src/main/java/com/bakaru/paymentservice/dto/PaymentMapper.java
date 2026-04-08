package com.bakaru.paymentservice.dto;

import com.bakaru.paymentservice.model.Payment;
import com.bakaru.paymentservice.model.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PaymentMapper {

    private final ObjectMapper objectMapper;

    public Payment toEntity(PaymentRequest request) {
        String itemsJson = null;
        if (request.getItems() != null) {
            try {
                itemsJson = objectMapper.writeValueAsString(request.getItems());
            } catch (JsonProcessingException e) {
                itemsJson = "[]";
            }
        }

        return Payment.builder()
                .orderId(request.getOrderId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .status(PaymentStatus.PENDING)
                .itemsJson(itemsJson)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .stripeSessionId(payment.getStripeSessionId())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}