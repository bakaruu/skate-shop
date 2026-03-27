package com.bakaru.orderservice.event;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPlacedEvent {

    private Long orderId;
    private Long customerId;
    private BigDecimal totalAmount;
    private List<OrderItemEvent> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemEvent {
        private Long productId;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}