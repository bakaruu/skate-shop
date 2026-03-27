package com.bakaru.orderservice.service;

import com.bakaru.orderservice.dto.OrderMapper;
import com.bakaru.orderservice.dto.OrderRequest;
import com.bakaru.orderservice.dto.OrderResponse;
import com.bakaru.orderservice.model.Order;
import com.bakaru.orderservice.model.OrderStatus;
import com.bakaru.orderservice.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerId());
        Order order = orderMapper.toEntity(request);
        Order saved = orderRepository.save(order);
        log.info("Order created with id: {}", saved.getId());
        // TODO: publicar evento Kafka order-placed
        return orderMapper.toResponse(saved);
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found with id: " + id));
        return orderMapper.toResponse(order);
    }

    public List<OrderResponse> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomerId(customerId)
                .stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found with id: " + id));
        order.setStatus(status);
        log.info("Order {} status updated to {}", id, status);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional
    public void cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found with id: " + id));
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Order {} cancelled", id);
        // TODO: publicar evento Kafka order-cancelled
    }
}