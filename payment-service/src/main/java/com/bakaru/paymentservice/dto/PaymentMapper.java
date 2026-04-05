package com.bakaru.paymentservice.dto;

import com.bakaru.paymentservice.model.Payment;
import com.bakaru.paymentservice.model.PaymentStatus;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class PaymentMapper {

    public Payment toEntity(PaymentRequest request) {
        return Payment.builder()
                .orderId(request.getOrderId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .status(PaymentStatus.PENDING)
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