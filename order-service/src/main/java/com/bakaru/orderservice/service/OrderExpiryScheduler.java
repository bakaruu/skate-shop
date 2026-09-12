package com.bakaru.orderservice.service;

import com.bakaru.orderservice.model.Order;
import com.bakaru.orderservice.model.OrderStatus;
import com.bakaru.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Safety net for abandoned checkouts: if a customer's order is created (reserving stock) but
 * neither a payment-completed nor payment-failed event ever arrives - browser closed before
 * Stripe redirect, network failure, or any other silent drop-off - nothing else in this system
 * would ever release that reservation. This sweep periodically cancels PENDING orders that have
 * sat around longer than {@code app.reservation-ttl-minutes}, reusing the same cancellation path
 * (and inventory release) as an explicit customer cancellation.
 * <p>
 * Disabled via {@code app.scheduling-enabled=false} in test resources: a {@code @SpringBootTest}
 * with a Testcontainers Postgres otherwise leaves this bean's background thread running past the
 * container's teardown, which then logs a scary (but harmless - {@code expireIfStillPending}'s own
 * per-order try/catch never even gets involved) connection-refused stack trace when the next tick
 * fires against a database that no longer exists. The scheduler's own logic is already covered by
 * {@code OrderExpirySchedulerTest} with mocks, so nothing is lost by keeping it out of test contexts.
 */
@Component
@ConditionalOnProperty(prefix = "app", name = "scheduling-enabled", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${app.reservation-ttl-minutes:30}")
    private long reservationTtlMinutes;

    @Scheduled(fixedDelayString = "${app.order-expiry-check-interval-ms:300000}")
    public void expireStaleOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(reservationTtlMinutes);
        List<Order> staleOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);

        if (staleOrders.isEmpty()) {
            return;
        }

        log.info("Found {} PENDING order(s) older than {} minutes, expiring them", staleOrders.size(), reservationTtlMinutes);
        for (Order order : staleOrders) {
            try {
                orderService.expireIfStillPending(order.getId());
            } catch (Exception e) {
                // one order's optimistic-lock conflict or transient failure shouldn't stop the
                // sweep from processing the rest of the batch.
                log.warn("Failed to expire order {}: {}", order.getId(), e.getMessage());
            }
        }
    }
}
