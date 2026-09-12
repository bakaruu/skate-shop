package com.bakaru.inventoryservice.service;

import com.bakaru.common.dto.ReservationLine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private InventoryService inventoryService;

    private OrderEventConsumer orderEventConsumer;

    @BeforeEach
    void setUp() {
        orderEventConsumer = new OrderEventConsumer(inventoryService, new ObjectMapper());
        // default: treat every event as new/unseen unless a test overrides this to exercise
        // the duplicate-delivery path explicitly. lenient() because the invalid-json tests
        // never reach this call at all (parsing fails first).
        lenient().when(inventoryService.tryMarkProcessed(any(), any())).thenReturn(true);
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
    void handlePaymentCompleted_whenAlreadyProcessed_skipsWithoutDecreasingStock() {
        when(inventoryService.tryMarkProcessed("payment-completed", 1L)).thenReturn(false);
        String payload = """
                {
                  "orderId": 1,
                  "customerId": 100,
                  "sessionId": "sess_123",
                  "status": "COMPLETED",
                  "items": [
                    {"productId": 10, "quantity": 2}
                  ]
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
    void handleOrderCancelled_withNoItems_doesNotThrow() {
        String payload = """
                {"orderId": 1, "customerId": 100}
                """;

        orderEventConsumer.handleOrderCancelled(payload);

        verifyNoInteractions(inventoryService);
    }

    @Test
    void handleOrderCancelled_whenNotPaid_releasesReservedStock() {
        String payload = """
                {
                  "orderId": 1,
                  "customerId": 100,
                  "paid": false,
                  "items": [
                    {"productId": 10, "quantity": 2},
                    {"productId": 11, "quantity": 1}
                  ]
                }
                """;

        orderEventConsumer.handleOrderCancelled(payload);

        verify(inventoryService).releaseBatch(List.of(
                new ReservationLine(10L, 2),
                new ReservationLine(11L, 1)));
        verify(inventoryService, never()).increaseStock(any(), any());
    }

    @Test
    void handleOrderCancelled_whenPaid_restocksQuantityInstead() {
        String payload = """
                {
                  "orderId": 1,
                  "customerId": 100,
                  "paid": true,
                  "items": [
                    {"productId": 10, "quantity": 2},
                    {"productId": 11, "quantity": 1}
                  ]
                }
                """;

        orderEventConsumer.handleOrderCancelled(payload);

        verify(inventoryService).increaseStock(10L, 2);
        verify(inventoryService).increaseStock(11L, 1);
        verify(inventoryService, never()).releaseBatch(any());
    }

    @Test
    void handleOrderCancelled_whenAlreadyProcessed_skipsWithoutMutatingStock() {
        when(inventoryService.tryMarkProcessed("order-cancelled", 1L)).thenReturn(false);
        String payload = """
                {
                  "orderId": 1,
                  "customerId": 100,
                  "paid": false,
                  "items": [
                    {"productId": 10, "quantity": 2}
                  ]
                }
                """;

        orderEventConsumer.handleOrderCancelled(payload);

        verify(inventoryService, never()).releaseBatch(any());
        verify(inventoryService, never()).increaseStock(any(), any());
    }

    @Test
    void handleOrderCancelled_withInvalidJson_doesNotThrow() {
        orderEventConsumer.handleOrderCancelled("invalid-json");

        verifyNoInteractions(inventoryService);
    }
}
