package com.bakaru.paymentservice.service;

import com.bakaru.common.event.PaymentCompletedEvent;
import com.bakaru.common.tracing.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    public void sendPaymentCompleted(PaymentCompletedEvent event) {
        send("payment-completed", event);
        log.info("Published payment-completed event for order: {}", event.getOrderId());
    }

    public void sendPaymentFailed(PaymentCompletedEvent event) {
        send("payment-failed", event);
        log.info("Published payment-failed event for order: {}", event.getOrderId());
    }

    /**
     * Carries the current request's correlation id (see CorrelationIdFilter) as a record
     * header, so a consumer picking this message up later - possibly in another service
     * entirely - can log under the same id via CorrelationIdRecordInterceptor.
     */
    private void send(String topic, PaymentCompletedEvent event) {
        ProducerRecord<String, PaymentCompletedEvent> record =
                new ProducerRecord<>(topic, event.getOrderId().toString(), event);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            record.headers().add(new RecordHeader(CorrelationIdFilter.HEADER, correlationId.getBytes(StandardCharsets.UTF_8)));
        }
        kafkaTemplate.send(record);
    }
}