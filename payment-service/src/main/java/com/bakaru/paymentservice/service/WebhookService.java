package com.bakaru.paymentservice.service;

import com.bakaru.paymentservice.event.PaymentCompletedEvent;
import com.bakaru.paymentservice.dto.PaymentRequest;
import com.bakaru.paymentservice.model.Payment;
import com.bakaru.paymentservice.repository.PaymentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final PaymentService paymentService;
    private final PaymentEventProducer paymentEventProducer;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public void processWebhook(String payload, String sigHeader) {
        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe webhook signature");
            throw new RuntimeException("Invalid webhook signature");
        }

        log.info("Received Stripe event: {}", event.getType());

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new RuntimeException("Failed to deserialize session"));
                paymentService.handleWebhook(session.getId(), true);

                Payment payment = paymentRepository.findByStripeSessionId(session.getId())
                        .orElseThrow(() -> new EntityNotFoundException("Payment not found"));

                List<PaymentCompletedEvent.OrderItem> items = parseItems(payment.getItemsJson());

                paymentEventProducer.sendPaymentCompleted(new PaymentCompletedEvent(
                        payment.getOrderId(),
                        payment.getCustomerId(),
                        session.getId(),
                        "COMPLETED",
                        items
                ));
                log.info("Payment completed for order: {}", payment.getOrderId());
            }
            case "checkout.session.expired" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new RuntimeException("Failed to deserialize session"));
                paymentService.handleWebhook(session.getId(), false);

                Payment payment = paymentRepository.findByStripeSessionId(session.getId())
                        .orElseThrow(() -> new EntityNotFoundException("Payment not found"));

                List<PaymentCompletedEvent.OrderItem> items = parseItems(payment.getItemsJson());

                paymentEventProducer.sendPaymentFailed(new PaymentCompletedEvent(
                        payment.getOrderId(),
                        payment.getCustomerId(),
                        session.getId(),
                        "FAILED",
                        items
                ));
                log.info("Payment failed for order: {}", payment.getOrderId());
            }
            default -> log.info("Unhandled event type: {}", event.getType());
        }
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