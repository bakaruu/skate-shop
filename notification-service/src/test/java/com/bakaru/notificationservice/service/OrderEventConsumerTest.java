package com.bakaru.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    private OrderEventConsumer orderEventConsumer;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        orderEventConsumer = new OrderEventConsumer(notificationService, mapper);
    }

    @Test
    void handleOrderPlaced_sendsOrderConfirmation() throws Exception {
        String payload = """
                {
                  "orderId": 1,
                  "customerId": 100,
                  "totalAmount": 79.99,
                  "items": []
                }
                """;

        orderEventConsumer.handleOrderPlaced(payload);

        verify(notificationService).sendOrderConfirmation(1L, 100L);
    }

    @Test
    void handleOrderCancelled_sendsOrderCancellation() throws Exception {
        String payload = """
                {"orderId": 1, "customerId": 100}
                """;

        orderEventConsumer.handleOrderCancelled(payload);

        verify(notificationService).sendOrderCancellation(1L, 100L);
    }

    @Test
    void handleOrderPlaced_withMultipleItems_stillSendsOneConfirmation() throws Exception {
        String payload = """
                {
                  "orderId": 2,
                  "customerId": 200,
                  "totalAmount": 159.98,
                  "items": [
                    {"productId": 10, "quantity": 1, "unitPrice": 79.99},
                    {"productId": 11, "quantity": 1, "unitPrice": 79.99}
                  ]
                }
                """;

        orderEventConsumer.handleOrderPlaced(payload);

        verify(notificationService, times(1)).sendOrderConfirmation(2L, 200L);
    }
}
