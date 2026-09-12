package com.bakaru.paymentservice.service;

import com.bakaru.paymentservice.dto.PaymentRequest;
import com.bakaru.common.event.PaymentCompletedEvent;
import com.bakaru.paymentservice.model.Payment;
import com.bakaru.paymentservice.model.PaymentStatus;
import com.bakaru.paymentservice.repository.PaymentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the payment status transitions triggered by Stripe webhooks. Kept as a bean separate
 * from WebhookService so @Transactional is actually applied through Spring's AOP proxy -
 * WebhookService calls into this bean rather than calling these methods on itself, which would
 * silently bypass the transactional advice (self-invocation never goes through the proxy).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTransitionService {

    private final PaymentEventProducer paymentEventProducer;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void completePayment(String sessionId) {
        transitionPayment(sessionId, PaymentStatus.COMPLETED, "COMPLETED", paymentEventProducer::sendPaymentCompleted);
    }

    @Transactional
    public void failPayment(String sessionId) {
        transitionPayment(sessionId, PaymentStatus.FAILED, "FAILED", paymentEventProducer::sendPaymentFailed);
    }

    private void transitionPayment(String sessionId, PaymentStatus newStatus, String eventStatus,
                                    Consumer<PaymentCompletedEvent> publish) {
        Payment payment = paymentRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found"));

        if (isTerminal(payment)) {
            log.info("Duplicate webhook for session {}, payment already {} - skipping", sessionId, payment.getStatus());
            return;
        }

        payment.setStatus(newStatus);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        List<PaymentCompletedEvent.OrderItem> items = parseItems(payment.getItemsJson());
        publish.accept(new PaymentCompletedEvent(
                payment.getOrderId(),
                payment.getCustomerId(),
                sessionId,
                eventStatus,
                items
        ));
        log.info("Payment {} for order: {}", newStatus, payment.getOrderId());
    }

    private boolean isTerminal(Payment payment) {
        return payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.FAILED;
    }

    private List<PaymentCompletedEvent.OrderItem> parseItems(String itemsJson) {
        if (itemsJson == null || itemsJson.isEmpty()) return Collections.emptyList();
        try {
            List<PaymentRequest.OrderItem> requestItems = objectMapper.readValue(
                    itemsJson, new TypeReference<List<PaymentRequest.OrderItem>>() {});
            return requestItems.stream()
                    .map(i -> new PaymentCompletedEvent.OrderItem(i.getProductId(), i.getQuantity()))
                    .toList();
        } catch (Exception e) {
            log.error("Error parsing items JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
