package com.bakaru.notificationservice.config;

import com.bakaru.common.tracing.CorrelationIdRecordInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.stereotype.Component;

/**
 * Spring Boot's auto-configured listener container factory has no property for a record
 * interceptor, so this wires one in directly after the factory bean is built, rather than
 * redefining the whole factory just to add one thing.
 */
@Component
@RequiredArgsConstructor
public class KafkaCorrelationConfig {

    private final ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory;

    @PostConstruct
    public void registerCorrelationIdInterceptor() {
        kafkaListenerContainerFactory.setRecordInterceptor(new CorrelationIdRecordInterceptor());
    }
}
