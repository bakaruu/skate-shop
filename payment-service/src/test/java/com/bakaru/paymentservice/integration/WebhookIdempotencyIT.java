package com.bakaru.paymentservice.integration;

import com.bakaru.paymentservice.client.OrderClient;
import com.bakaru.paymentservice.model.Payment;
import com.bakaru.paymentservice.model.PaymentStatus;
import com.bakaru.paymentservice.repository.PaymentRepository;
import com.bakaru.paymentservice.service.PaymentEventProducer;
import com.bakaru.paymentservice.service.PaymentTransitionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Proves webhook idempotency against a real Postgres-backed Payment row (not a mocked
 * repository): processing the same Stripe session id twice must only transition the status
 * once and only publish the Kafka event once.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "stripe.secret-key=sk_test_dummy",
        "stripe.webhook-secret=whsec_dummy"
})
class WebhookIdempotencyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Autowired
    private PaymentTransitionService paymentTransitionService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentEventProducer paymentEventProducer;

    @MockitoBean
    private OrderClient orderClient;

    @Test
    void completePayment_calledTwiceForSameSession_publishesOnlyOnce() {
        Payment payment = paymentRepository.save(Payment.builder()
                .orderId(500L)
                .customerId(100L)
                .amount(new BigDecimal("79.99"))
                .status(PaymentStatus.PENDING)
                .stripeSessionId("sess_idempotency_test")
                .itemsJson("[]")
                .createdAt(LocalDateTime.now())
                .build());

        paymentTransitionService.completePayment("sess_idempotency_test");
        paymentTransitionService.completePayment("sess_idempotency_test");

        Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentEventProducer, times(1)).sendPaymentCompleted(any());
    }
}
