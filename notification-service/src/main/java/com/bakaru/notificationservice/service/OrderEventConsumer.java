package com.bakaru.notificationservice.service;

import com.bakaru.common.event.OrderCancelledEvent;
import com.bakaru.common.event.OrderPlacedEvent;
import com.bakaru.common.event.PaymentCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-placed", groupId = "notification-service")
    public void handleOrderPlaced(String payload) throws Exception {
        OrderPlacedEvent event = objectMapper.readValue(payload, OrderPlacedEvent.class);
        log.info("Received order-placed event for orderId: {}", event.getOrderId());
        notificationService.sendOrderConfirmation(event.getOrderId(), event.getCustomerId());
    }

    @KafkaListener(topics = "order-cancelled", groupId = "notification-service")
    public void handleOrderCancelled(String payload) throws Exception {
        OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
        log.info("Received order-cancelled event for orderId: {}", event.getOrderId());
        notificationService.sendOrderCancellation(event.getOrderId(), event.getCustomerId());
    }

    @KafkaListener(topics = "payment-completed", groupId = "notification-service")
    public void handlePaymentCompleted(String payload) throws Exception {
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
        log.info("Received payment-completed event for orderId: {}", event.getOrderId());
        notificationService.sendPaymentConfirmation(event.getOrderId(), event.getCustomerId());
    }

    @KafkaListener(topics = "payment-failed", groupId = "notification-service")
    public void handlePaymentFailed(String payload) throws Exception {
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
        log.info("Received payment-failed event for orderId: {}", event.getOrderId());
        notificationService.sendPaymentFailed(event.getOrderId(), event.getCustomerId());
    }
}