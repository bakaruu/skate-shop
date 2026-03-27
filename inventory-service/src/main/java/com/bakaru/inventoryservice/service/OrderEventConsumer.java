package com.bakaru.inventoryservice.service;

import com.bakaru.inventoryservice.event.OrderCancelledEvent;
import com.bakaru.inventoryservice.event.OrderPlacedEvent;
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

    @KafkaListener(topics = "order-placed", groupId = "inventory-service")
    public void handleOrderPlaced(String payload) throws Exception {
        OrderPlacedEvent event = objectMapper.readValue(payload, OrderPlacedEvent.class);
        log.info("Received order-placed event for orderId: {}", event.getOrderId());
        event.getItems().forEach(item -> {
            try {
                inventoryService.decreaseStock(item.getProductId(), item.getQuantity());
                log.info("Stock decreased for product {} by {}",
                        item.getProductId(), item.getQuantity());
            } catch (Exception e) {
                log.error("Failed to decrease stock for product {}: {}",
                        item.getProductId(), e.getMessage());
            }
        });
    }

    @KafkaListener(topics = "order-cancelled", groupId = "inventory-service")
    public void handleOrderCancelled(String payload) throws Exception {
        OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
        log.info("Received order-cancelled event for orderId: {}", event.getOrderId());
    }
}