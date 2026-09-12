package com.bakaru.common.event;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCancelledEvent {

    private Long orderId;
    private Long customerId;
    private List<OrderItemEvent> items;
    /** Whether the order had already been paid (and its stock physically decremented) at the
     *  time it was cancelled - tells inventory-service whether to restock quantity or just
     *  release a pending reservation. */
    private boolean paid;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemEvent {
        private Long productId;
        private Integer quantity;
    }
}
