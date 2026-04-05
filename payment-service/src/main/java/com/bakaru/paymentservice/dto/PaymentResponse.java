package com.bakaru.paymentservice.dto;

import com.bakaru.paymentservice.model.PaymentStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private Long orderId;
    private Long customerId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String stripeSessionId;
    private String checkoutUrl;
    private LocalDateTime createdAt;
}