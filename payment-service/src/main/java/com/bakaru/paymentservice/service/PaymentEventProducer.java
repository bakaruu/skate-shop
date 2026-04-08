package com.bakaru.paymentservice.service;

import com.bakaru.paymentservice.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    public void sendPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send("payment-completed", event.getOrderId().toString(), event);
        log.info("Published payment-completed event for order: {}", event.getOrderId());
    }

    public void sendPaymentFailed(PaymentCompletedEvent event) {
        kafkaTemplate.send("payment-failed", event.getOrderId().toString(), event);
        log.info("Published payment-failed event for order: {}", event.getOrderId());
    }
}