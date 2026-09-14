package com.bakaru.common.tracing;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The Kafka-side counterpart of {@link CorrelationIdFilter}/{@link FeignCorrelationIdInterceptor}:
 * pulls the correlation id off the record's headers (put there by the producer, see
 * OrderEventProducer/PaymentEventProducer) and puts it in MDC for the duration of the listener
 * method, so a log line from a Kafka consumer can still be tied back to the HTTP request that
 * originally triggered it - or, if the id is missing (e.g. an older message, or one published
 * outside this app), mints one so the consumer's own log lines are still traceable together.
 * Wire this into a service's listener container factory with a small
 * {@code @PostConstruct}-based config, since Spring Boot's auto-configured factory has no
 * property for it.
 */
public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        Header header = record.headers().lastHeader(CorrelationIdFilter.HEADER);
        String correlationId = header != null
                ? new String(header.value(), StandardCharsets.UTF_8)
                : UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }
}
