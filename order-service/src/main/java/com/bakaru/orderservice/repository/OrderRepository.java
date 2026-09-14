package com.bakaru.orderservice.repository;

import com.bakaru.orderservice.model.Order;
import com.bakaru.orderservice.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerId(Long customerId);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime cutoff);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}