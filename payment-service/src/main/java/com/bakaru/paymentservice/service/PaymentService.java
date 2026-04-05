package com.bakaru.paymentservice.service;

import com.bakaru.paymentservice.dto.CheckoutResponse;
import com.bakaru.paymentservice.dto.PaymentMapper;
import com.bakaru.paymentservice.dto.PaymentRequest;
import com.bakaru.paymentservice.dto.PaymentResponse;
import com.bakaru.paymentservice.model.Payment;
import com.bakaru.paymentservice.model.PaymentStatus;
import com.bakaru.paymentservice.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Transactional
    public CheckoutResponse createCheckoutSession(PaymentRequest request) throws StripeException {
        Stripe.apiKey = stripeSecretKey;

        Payment payment = paymentMapper.toEntity(request);
        Payment saved = paymentRepository.save(payment);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("http://localhost:4200/payment/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl("http://localhost:4200/payment/cancel")
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("eur")
                                .setUnitAmount(request.getAmount()
                                        .multiply(java.math.BigDecimal.valueOf(100))
                                        .longValue())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Skate Shop Order #" + request.getOrderId())
                                        .build())
                                .build())
                        .build())
                .putMetadata("orderId", request.getOrderId().toString())
                .putMetadata("paymentId", saved.getId().toString())
                .build();

        Session session = Session.create(params);

        saved.setStripeSessionId(session.getId());
        paymentRepository.save(saved);

        log.info("Checkout session created for order: {}", request.getOrderId());

        return CheckoutResponse.builder()
                .checkoutUrl(session.getUrl())
                .sessionId(session.getId())
                .orderId(request.getOrderId())
                .build();
    }

    @Transactional
    public void handleWebhook(String sessionId, boolean success) {
        Payment payment = paymentRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for session: " + sessionId));

        payment.setStatus(success ? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
        payment.setUpdatedAt(java.time.LocalDateTime.now());
        paymentRepository.save(payment);

        log.info("Payment {} for order {} - status: {}",
                payment.getId(), payment.getOrderId(), payment.getStatus());

        // TODO: publicar evento Kafka payment-completed o payment-failed
    }

    public PaymentResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for order: " + orderId));
        return paymentMapper.toResponse(payment);
    }

    public List<PaymentResponse> getPaymentsByCustomer(Long customerId) {
        return paymentRepository.findByCustomerId(customerId)
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }
}