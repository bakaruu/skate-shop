package com.bakaru.paymentservice.controller;

import com.bakaru.paymentservice.dto.CheckoutResponse;
import com.bakaru.paymentservice.dto.PaymentRequest;
import com.bakaru.paymentservice.dto.PaymentResponse;
import com.bakaru.paymentservice.service.PaymentService;
import com.bakaru.paymentservice.service.WebhookService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final WebhookService webhookService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> createCheckout(
            @Valid @RequestBody PaymentRequest request) throws StripeException {
        return ResponseEntity.ok(paymentService.createCheckoutSession(request));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        webhookService.processWebhook(payload, sigHeader);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<PaymentResponse>> getByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(paymentService.getPaymentsByCustomer(customerId));
    }
}