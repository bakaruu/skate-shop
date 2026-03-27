package com.bakaru.orderservice.repository;

import com.bakaru.orderservice.model.Order;
import com.bakaru.orderservice.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerId(Long customerId);
    List<Order> findByStatus(OrderStatus status);
}