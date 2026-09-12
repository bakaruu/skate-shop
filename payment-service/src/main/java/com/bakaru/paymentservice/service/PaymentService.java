package com.bakaru.paymentservice.service;

import com.bakaru.paymentservice.client.OrderClient;
import com.bakaru.paymentservice.client.OrderClientResponse;
import com.bakaru.paymentservice.dto.CheckoutResponse;
import com.bakaru.paymentservice.dto.PaymentMapper;
import com.bakaru.paymentservice.dto.PaymentRequest;
import com.bakaru.common.exception.UpstreamServiceException;
import com.bakaru.paymentservice.dto.PaymentResponse;
import com.bakaru.paymentservice.model.Payment;
import com.bakaru.paymentservice.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final OrderClient orderClient;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * Must stay in sync with order-service's app.reservation-ttl-minutes: that's when
     * OrderExpiryScheduler cancels an unpaid order and releases its stock. Expiring the Stripe
     * session at the same time means a customer can't complete a payment for a session we've
     * already given up on order-side. Stripe requires at least 30 minutes, so a lower value is
     * clamped rather than sent as-is (which Stripe would otherwise reject outright).
     */
    @Value("${app.reservation-ttl-minutes:30}")
    private long reservationTtlMinutes;

    /**
     * Deliberately not @Transactional: the only DB write here is the single save() at the end,
     * which Spring Data JPA already commits atomically on its own. Wrapping the whole method
     * would hold a pooled DB connection open across the Feign call to order-service AND the
     * Stripe API call - two blocking external round-trips - for no benefit, since building the
     * Stripe session before persisting anything means there's no partial/orphaned row to protect
     * against if either external call fails.
     */
    public CheckoutResponse createCheckoutSession(PaymentRequest request) throws StripeException {
        Stripe.apiKey = stripeSecretKey;

        OrderClientResponse order = fetchOrder(request.getOrderId());
        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalStateException(
                    "Order " + order.getId() + " is not payable, current status: " + order.getStatus());
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendBaseUrl + "/payment/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendBaseUrl + "/payment/cancel")
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("eur")
                                .setUnitAmount(order.getTotalAmount()
                                        .multiply(java.math.BigDecimal.valueOf(100))
                                        .longValue())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Skate Shop Order #" + order.getId())
                                        .build())
                                .build())
                        .build())
                .putMetadata("orderId", order.getId().toString())
                .setExpiresAt(Instant.now().plusSeconds(Math.max(reservationTtlMinutes, 30) * 60).getEpochSecond())
                .build();

        Session session = Session.create(params);

        Payment payment = paymentMapper.toEntity(order);
        payment.setStripeSessionId(session.getId());
        paymentRepository.save(payment);

        log.info("Checkout session created for order: {}", order.getId());

        return CheckoutResponse.builder()
                .checkoutUrl(session.getUrl())
                .sessionId(session.getId())
                .orderId(order.getId())
                .build();
    }

    private OrderClientResponse fetchOrder(Long orderId) {
        try {
            return orderClient.getOrder(orderId);
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("Order not found: " + orderId);
        } catch (FeignException e) {
            log.error("Failed to fetch order from order-service", e);
            throw new UpstreamServiceException("Order service unavailable, please retry");
        }
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