package com.bakaru.inventoryservice.service;

import com.bakaru.inventoryservice.event.OrderCancelledEvent;
import com.bakaru.inventoryservice.event.PaymentCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-completed", groupId = "inventory-service")
    public void handlePaymentCompleted(String payload) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
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

    @KafkaListener(topics = "order-cancelled", groupId = "inventory-service")
    public void handleOrderCancelled(String payload) {
        try {
            OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
            log.info("Order cancelled: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Error processing order-cancelled event: {}", e.getMessage());
        }
    }
}