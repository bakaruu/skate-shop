package com.bakaru.paymentservice.service;

import com.bakaru.common.event.PaymentCompletedEvent;
import com.bakaru.paymentservice.model.Payment;
import com.bakaru.paymentservice.model.PaymentStatus;
import com.bakaru.paymentservice.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentTransitionServiceTest {

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentTransitionService paymentTransitionService;

    private Payment payment;

    @BeforeEach
    void setUp() {
        try {
            var field = PaymentTransitionService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(paymentTransitionService, new ObjectMapper());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .customerId(100L)
                .amount(new BigDecimal("79.99"))
                .status(PaymentStatus.PENDING)
                .stripeSessionId("sess_123")
                .itemsJson("[{\"productId\":10,\"quantity\":2}]")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void completePayment_setsStatusCompletedAndPublishesEvent() {
        when(paymentRepository.findByStripeSessionId("sess_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentTransitionService.completePayment("sess_123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentEventProducer).sendPaymentCompleted(any(PaymentCompletedEvent.class));
    }

    @Test
    void completePayment_whenAlreadyCompleted_isIdempotentAndPublishesNothing() {
        payment.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findByStripeSessionId("sess_123")).thenReturn(Optional.of(payment));

        paymentTransitionService.completePayment("sess_123");
        paymentTransitionService.completePayment("sess_123");

        verify(paymentEventProducer, never()).sendPaymentCompleted(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void completePayment_whenSessionNotFound_throwsEntityNotFoundException() {
        when(paymentRepository.findByStripeSessionId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentTransitionService.completePayment("unknown"))
                .isInstanceOf(EntityNotFoundException.class);

        verify(paymentEventProducer, never()).sendPaymentCompleted(any());
    }

    @Test
    void failPayment_setsStatusFailedAndPublishesEvent() {
        when(paymentRepository.findByStripeSessionId("sess_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentTransitionService.failPayment("sess_123");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        ArgumentCaptor<PaymentCompletedEvent> captor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(paymentEventProducer).sendPaymentFailed(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(10L);
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void failPayment_whenAlreadyTerminal_isIdempotent() {
        payment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findByStripeSessionId("sess_123")).thenReturn(Optional.of(payment));

        paymentTransitionService.failPayment("sess_123");

        verify(paymentEventProducer, never()).sendPaymentFailed(any());
        verify(paymentRepository, never()).save(any());
    }
}
