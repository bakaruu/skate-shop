package com.bakaru.inventoryservice.service;

import com.bakaru.common.dto.ReservationLine;
import com.bakaru.common.event.OrderCancelledEvent;
import com.bakaru.common.event.PaymentCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";
    private static final String ORDER_CANCELLED_TOPIC = "order-cancelled";

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = PAYMENT_COMPLETED_TOPIC, groupId = "inventory-service")
    public void handlePaymentCompleted(String payload) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);

            if (!inventoryService.tryMarkProcessed(PAYMENT_COMPLETED_TOPIC, event.getOrderId())) {
                log.info("Duplicate payment-completed event for order {}, skipping", event.getOrderId());
                return;
            }

            log.info("Payment completed for order: {}, decreasing stock", event.getOrderId());
            if (event.getItems() != null) {
                for (PaymentCompletedEvent.OrderItem item : event.getItems()) {
                    inventoryService.decreaseStock(item.getProductId(), item.getQuantity());
                }
            }
        } catch (Exception e) {
            log.error("Error processing payment-completed event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = ORDER_CANCELLED_TOPIC, groupId = "inventory-service")
    public void handleOrderCancelled(String payload) {
        try {
            OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);

            if (event.getItems() == null) {
                return;
            }

            if (!inventoryService.tryMarkProcessed(ORDER_CANCELLED_TOPIC, event.getOrderId())) {
                log.info("Duplicate order-cancelled event for order {}, skipping", event.getOrderId());
                return;
            }

            if (event.isPaid()) {
                // Stock was already physically decremented (decreaseStock) when the order was
                // paid, so cancelling it now must restore quantity - not just release a
                // reservation, which is already 0 for this order at this point.
                log.info("Paid order {} cancelled, restocking quantity", event.getOrderId());
                for (OrderCancelledEvent.OrderItemEvent item : event.getItems()) {
                    inventoryService.increaseStock(item.getProductId(), item.getQuantity());
                }
            } else {
                log.info("Order {} cancelled, releasing reserved stock", event.getOrderId());
                List<ReservationLine> lines = event.getItems().stream()
                        .map(item -> new ReservationLine(item.getProductId(), item.getQuantity()))
                        .toList();
                inventoryService.releaseBatch(lines);
            }
        } catch (Exception e) {
            log.error("Error processing order-cancelled event: {}", e.getMessage());
        }
    }
}