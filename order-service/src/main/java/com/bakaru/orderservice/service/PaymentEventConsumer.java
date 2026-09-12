package com.bakaru.orderservice.service;

import com.bakaru.common.event.PaymentCompletedEvent;
import com.bakaru.orderservice.model.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-completed", groupId = "order-service")
    public void handlePaymentCompleted(String payload) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
            log.info("Payment completed for order: {}, updating status to PAID", event.getOrderId());
            orderService.updateOrderStatus(event.getOrderId(), OrderStatus.PAID);
        } catch (Exception e) {
            log.error("Error processing payment-completed event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment-failed", groupId = "order-service")
    public void handlePaymentFailed(String payload) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
            log.info("Payment failed for order: {}, cancelling order", event.getOrderId());
            orderService.handlePaymentFailed(event.getOrderId());
        } catch (Exception e) {
            log.error("Error processing payment-failed event: {}", e.getMessage());
        }
    }
}