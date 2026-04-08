package com.bakaru.orderservice.service;

import com.bakaru.orderservice.model.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentEventConsumer paymentEventConsumer;

    @BeforeEach
    void setUp() {
        // Inject a real ObjectMapper since it's a utility, not a dependency to mock
        try {
            var field = PaymentEventConsumer.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            field.set(paymentEventConsumer, mapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handlePaymentCompleted_whenStatusCompleted_updatesOrderToPaid() {
        String payload = """
                {"orderId": 1, "customerId": 100, "status": "COMPLETED"}
                """;

        paymentEventConsumer.handlePaymentCompleted(payload);

        verify(orderService).updateOrderStatus(1L, OrderStatus.PAID);
    }

    @Test
    void handlePaymentCompleted_whenStatusFailed_cancelsOrder() {
        String payload = """
                {"orderId": 1, "customerId": 100, "status": "FAILED"}
                """;

        paymentEventConsumer.handlePaymentCompleted(payload);

        verify(orderService).updateOrderStatus(1L, OrderStatus.CANCELLED);
    }

    @Test
    void handlePaymentCompleted_withInvalidJson_doesNotThrow() {
        String invalidPayload = "not-valid-json";

        paymentEventConsumer.handlePaymentCompleted(invalidPayload);

        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void handlePaymentCompleted_withUnknownStatus_doesNotUpdateOrder() {
        String payload = """
                {"orderId": 1, "customerId": 100, "status": "UNKNOWN"}
                """;

        paymentEventConsumer.handlePaymentCompleted(payload);

        verify(orderService, never()).updateOrderStatus(any(), any());
    }
}
