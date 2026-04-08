package com.bakaru.inventoryservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private InventoryService inventoryService;

    private OrderEventConsumer orderEventConsumer;

    @BeforeEach
    void setUp() {
        orderEventConsumer = new OrderEventConsumer(inventoryService, new ObjectMapper());
    }

    @Test
    void handlePaymentCompleted_decreasesStockForEachItem() {
        String payload = """
                {
                  "orderId": 1,
                  "customerId": 100,
                  "sessionId": "sess_123",
                  "status": "COMPLETED",
                  "items": [
                    {"productId": 10, "quantity": 2},
                    {"productId": 11, "quantity": 1}
                  ]
                }
                """;

        orderEventConsumer.handlePaymentCompleted(payload);

        verify(inventoryService).decreaseStock(10L, 2);
        verify(inventoryService).decreaseStock(11L, 1);
    }

    @Test
    void handlePaymentCompleted_withNoItems_doesNotDecreaseStock() {
        String payload = """
                {
                  "orderId": 1,
                  "customerId": 100,
                  "sessionId": "sess_123",
                  "status": "COMPLETED",
                  "items": []
                }
                """;

        orderEventConsumer.handlePaymentCompleted(payload);

        verify(inventoryService, never()).decreaseStock(any(), any());
    }

    @Test
    void handlePaymentCompleted_withInvalidJson_doesNotThrow() {
        orderEventConsumer.handlePaymentCompleted("invalid-json");

        verify(inventoryService, never()).decreaseStock(any(), any());
    }

    @Test
    void handleOrderCancelled_withValidPayload_doesNotThrow() {
        String payload = """
                {"orderId": 1, "customerId": 100}
                """;

        orderEventConsumer.handleOrderCancelled(payload);

        verifyNoInteractions(inventoryService);
    }

    @Test
    void handleOrderCancelled_withInvalidJson_doesNotThrow() {
        orderEventConsumer.handleOrderCancelled("invalid-json");

        verifyNoInteractions(inventoryService);
    }
}
