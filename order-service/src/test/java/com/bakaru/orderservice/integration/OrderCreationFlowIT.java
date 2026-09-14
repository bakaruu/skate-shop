package com.bakaru.orderservice.integration;

import com.bakaru.orderservice.client.InventoryClient;
import com.bakaru.orderservice.client.ProductClient;
import com.bakaru.orderservice.client.ProductClientResponse;
import com.bakaru.orderservice.dto.OrderItemRequest;
import com.bakaru.orderservice.dto.OrderRequest;
import com.bakaru.orderservice.dto.OrderResponse;
import com.bakaru.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Exercises the real order-creation flow against a live Postgres and Kafka broker, mocking only
 * the external service boundaries (product-service, inventory-service) that order-service talks
 * to over Feign - everything else (persistence, Kafka publish) runs for real.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "app.scheduling-enabled=false")
class OrderCreationFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private ProductClient productClient;

    @MockitoBean
    private InventoryClient inventoryClient;

    @Test
    void createOrder_persistsOrderAndPublishesRealKafkaEvent() {
        when(productClient.getByIds(List.of(10L)))
                .thenReturn(List.of(new ProductClientResponse(10L, "Deck", new BigDecimal("79.99"), true)));
        doNothing().when(inventoryClient).reserve(anyList());

        OrderRequest request = OrderRequest.builder()
                .customerId(100L)
                .items(List.of(OrderItemRequest.builder().productId(10L).quantity(2).build()))
                .build();

        OrderResponse response = orderService.createOrder(request, null);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getTotalAmount()).isEqualByComparingTo("159.98");

        JsonNode event = pollOrderPlacedEvent(response.getId());
        assertThat(event.get("orderId").asLong()).isEqualTo(response.getId());
        assertThat(event.get("customerId").asLong()).isEqualTo(100L);
    }

    private JsonNode pollOrderPlacedEvent(Long expectedOrderId) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
        ObjectMapper objectMapper = new ObjectMapper();

        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer()) {
            consumer.subscribe(List.of("order-placed"));
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        JsonNode node = objectMapper.readTree(record.value());
                        if (node.get("orderId").asLong() == expectedOrderId) {
                            return node;
                        }
                    } catch (Exception ignored) {
                        // not the JSON payload we're looking for
                    }
                }
            }
        }
        throw new AssertionError("Did not receive order-placed event for order " + expectedOrderId + " within timeout");
    }
}
