package com.bakaru.orderservice.service;

import com.bakaru.orderservice.event.OrderCancelledEvent;
import com.bakaru.orderservice.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendOrderPlaced(OrderPlacedEvent event) {
        log.info("Publishing order-placed event for orderId: {}", event.getOrderId());
        kafkaTemplate.send("order-placed", event.getOrderId().toString(), event);
    }

    public void sendOrderCancelled(OrderCancelledEvent event) {
        log.info("Publishing order-cancelled event for orderId: {}", event.getOrderId());
        kafkaTemplate.send("order-cancelled", event.getOrderId().toString(), event);
    }
}