package com.bakaru.paymentservice.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final PaymentService paymentService;

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
                log.info("Payment completed for session: {}", session.getId());
                paymentService.handleWebhook(session.getId(), true);
            }
            case "checkout.session.expired" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new RuntimeException("Failed to deserialize session"));
                log.info("Payment failed/expired for session: {}", session.getId());
                paymentService.handleWebhook(session.getId(), false);
            }
            default -> log.info("Unhandled event type: {}", event.getType());
        }
    }
}