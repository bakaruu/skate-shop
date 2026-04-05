package com.bakaru.paymentservice.repository;

import com.bakaru.paymentservice.model.Payment;
import com.bakaru.paymentservice.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);
    Optional<Payment> findByStripeSessionId(String stripeSessionId);
    List<Payment> findByCustomerId(Long customerId);
    List<Payment> findByStatus(PaymentStatus status);
}