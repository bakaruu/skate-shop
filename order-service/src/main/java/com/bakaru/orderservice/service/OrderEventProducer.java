package com.bakaru.orderservice.service;

import com.bakaru.common.event.OrderCancelledEvent;
import com.bakaru.common.event.OrderPlacedEvent;
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
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendOrderPlaced(OrderPlacedEvent event) {
        log.info("Publishing order-placed event for orderId: {}", event.getOrderId());
        send("order-placed", event.getOrderId().toString(), event);
    }

    public void sendOrderCancelled(OrderCancelledEvent event) {
        log.info("Publishing order-cancelled event for orderId: {}", event.getOrderId());
        send("order-cancelled", event.getOrderId().toString(), event);
    }

    /**
     * Carries the current request's correlation id (see CorrelationIdFilter) as a record
     * header, so a consumer picking this message up later - possibly in another service
     * entirely - can log under the same id via CorrelationIdRecordInterceptor.
     */
    private void send(String topic, String key, Object payload) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            record.headers().add(new RecordHeader(CorrelationIdFilter.HEADER, correlationId.getBytes(StandardCharsets.UTF_8)));
        }
        kafkaTemplate.send(record);
    }
}