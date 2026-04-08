package com.bakaru.orderservice.service;

import com.bakaru.orderservice.event.PaymentCompletedEvent;
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

            if ("COMPLETED".equals(event.getStatus())) {
                orderService.updateOrderStatus(event.getOrderId(), OrderStatus.PAID);
            } else if ("FAILED".equals(event.getStatus())) {
                orderService.updateOrderStatus(event.getOrderId(), OrderStatus.CANCELLED);
            }
        } catch (Exception e) {
            log.error("Error processing payment-completed event: {}", e.getMessage());
        }
    }
}