package com.bakaru.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
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

    private final PaymentTransitionService paymentTransitionService;
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
                try {
                    String sessionId = extractSessionId(event);
                    log.info("Processing checkout.session.completed for session: {}", sessionId);
                    paymentTransitionService.completePayment(sessionId);
                } catch (Exception e) {
                    log.error("Error processing checkout.session.completed: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }
            case "checkout.session.expired" -> {
                try {
                    String sessionId = extractSessionId(event);
                    log.info("Processing checkout.session.expired for session: {}", sessionId);
                    paymentTransitionService.failPayment(sessionId);
                } catch (Exception e) {
                    log.error("Error processing checkout.session.expired: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }
            default -> log.info("Unhandled event type: {}", event.getType());
        }
    }

    /**
     * Stripe's SDK doesn't always deserialize the event payload into a concrete Session
     * instance (depends on API version/event shape); fall back to parsing the raw JSON for
     * the session id in that case. Applied to every event type handled here, not just some.
     */
    private String extractSessionId(Event event) throws Exception {
        StripeObject stripeObject = event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);

        if (stripeObject instanceof Session session) {
            return session.getId();
        }
        String raw = event.getDataObjectDeserializer().getRawJson();
        return objectMapper.readTree(raw).get("id").asText();
    }
}
