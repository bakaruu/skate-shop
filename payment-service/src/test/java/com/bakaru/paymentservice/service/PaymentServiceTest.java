package com.bakaru.paymentservice.service;

import com.bakaru.paymentservice.dto.PaymentMapper;
import com.bakaru.paymentservice.dto.PaymentResponse;
import com.bakaru.paymentservice.model.Payment;
import com.bakaru.paymentservice.model.PaymentStatus;
import com.bakaru.paymentservice.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentService paymentService;

    private Payment payment;
    private PaymentResponse paymentResponse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "stripeSecretKey", "sk_test_dummy");

        payment = Payment.builder()
                .id(1L)
                .orderId(10L)
                .customerId(100L)
                .amount(new BigDecimal("79.99"))
                .status(PaymentStatus.PENDING)
                .stripeSessionId("sess_123")
                .createdAt(LocalDateTime.now())
                .build();

        paymentResponse = PaymentResponse.builder()
                .id(1L)
                .orderId(10L)
                .customerId(100L)
                .amount(new BigDecimal("79.99"))
                .status(PaymentStatus.COMPLETED)
                .stripeSessionId("sess_123")
                .build();
    }

    @Test
    void handleWebhook_whenSuccess_setsStatusCompleted() {
        when(paymentRepository.findByStripeSessionId("sess_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.handleWebhook("sess_123", true);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getUpdatedAt()).isNotNull();
        verify(paymentRepository).save(payment);
    }

    @Test
    void handleWebhook_whenFailed_setsStatusFailed() {
        when(paymentRepository.findByStripeSessionId("sess_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        paymentService.handleWebhook("sess_123", false);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void handleWebhook_whenSessionNotFound_throwsEntityNotFoundException() {
        when(paymentRepository.findByStripeSessionId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.handleWebhook("unknown", true))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("unknown");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void getPaymentByOrderId_whenExists_returnsResponse() {
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(paymentResponse);

        PaymentResponse result = paymentService.getPaymentByOrderId(10L);

        assertThat(result.getOrderId()).isEqualTo(10L);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void getPaymentByOrderId_whenNotExists_throwsEntityNotFoundException() {
        when(paymentRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByOrderId(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getPaymentsByCustomer_returnsListForCustomer() {
        when(paymentRepository.findByCustomerId(100L)).thenReturn(List.of(payment));
        when(paymentMapper.toResponse(payment)).thenReturn(paymentResponse);

        List<PaymentResponse> result = paymentService.getPaymentsByCustomer(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomerId()).isEqualTo(100L);
    }

    @Test
    void getPaymentsByCustomer_whenNoPayments_returnsEmptyList() {
        when(paymentRepository.findByCustomerId(999L)).thenReturn(List.of());

        List<PaymentResponse> result = paymentService.getPaymentsByCustomer(999L);

        assertThat(result).isEmpty();
    }
}
