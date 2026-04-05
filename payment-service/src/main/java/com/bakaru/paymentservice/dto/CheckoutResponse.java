package com.bakaru.paymentservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutResponse {

    private String checkoutUrl;
    private String sessionId;
    private Long orderId;
}