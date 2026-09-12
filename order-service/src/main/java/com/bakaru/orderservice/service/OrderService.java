package com.bakaru.orderservice.service;

import com.bakaru.orderservice.client.InventoryClient;
import com.bakaru.orderservice.client.ProductClient;
import com.bakaru.orderservice.client.ProductClientResponse;
import com.bakaru.common.dto.ReservationLine;
import com.bakaru.orderservice.dto.OrderItemRequest;
import com.bakaru.orderservice.dto.OrderMapper;
import com.bakaru.orderservice.dto.OrderRequest;
import com.bakaru.orderservice.dto.OrderResponse;
import com.bakaru.common.exception.UpstreamServiceException;
import com.bakaru.common.event.OrderCancelledEvent;
import com.bakaru.common.event.OrderPlacedEvent;
import com.bakaru.orderservice.model.Order;
import com.bakaru.orderservice.model.OrderStatus;
import com.bakaru.orderservice.repository.OrderRepository;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderEventProducer orderEventProducer;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerId());

        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .distinct()
                .toList();

        Map<Long, BigDecimal> pricesByProductId = fetchAuthoritativePrices(productIds);

        List<ReservationLine> reservationLines = request.getItems().stream()
                .map(item -> new ReservationLine(item.getProductId(), item.getQuantity()))
                .toList();
        reserveStock(reservationLines);

        Order saved;
        try {
            Order order = orderMapper.toEntity(request, pricesByProductId);
            saved = orderRepository.save(order);
            log.info("Order created with id: {}", saved.getId());

            OrderPlacedEvent event = OrderPlacedEvent.builder()
                    .orderId(saved.getId())
                    .customerId(saved.getCustomerId())
                    .totalAmount(saved.getTotalAmount())
                    .items(saved.getItems().stream()
                            .map(item -> OrderPlacedEvent.OrderItemEvent.builder()
                                    .productId(item.getProductId())
                                    .quantity(item.getQuantity())
                                    .unitPrice(item.getUnitPrice())
                                    .build())
                            .toList())
                    .build();
            orderEventProducer.sendOrderPlaced(event);
        } catch (RuntimeException e) {
            log.error("Order creation failed after stock was reserved, releasing reservation", e);
            try {
                inventoryClient.release(reservationLines);
            } catch (RuntimeException releaseEx) {
                log.error("Failed to release stock reservation after failed order creation", releaseEx);
            }
            throw e;
        }

        return orderMapper.toResponse(saved);
    }

    private Map<Long, BigDecimal> fetchAuthoritativePrices(List<Long> productIds) {
        List<ProductClientResponse> products;
        try {
            products = productClient.getByIds(productIds);
        } catch (FeignException e) {
            log.error("Failed to fetch product prices from product-service", e);
            throw new UpstreamServiceException("Pricing service unavailable, please retry");
        }

        Map<Long, ProductClientResponse> byId = products.stream()
                .collect(java.util.stream.Collectors.toMap(ProductClientResponse::getId, Function.identity()));

        for (Long productId : productIds) {
            ProductClientResponse product = byId.get(productId);
            if (product == null) {
                throw new IllegalArgumentException("Unknown product id: " + productId);
            }
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new IllegalArgumentException("Product is not available: " + productId);
            }
        }

        return byId.values().stream()
                .collect(java.util.stream.Collectors.toMap(ProductClientResponse::getId, ProductClientResponse::getPrice));
    }

    private void reserveStock(List<ReservationLine> lines) {
        try {
            inventoryClient.reserve(lines);
        } catch (FeignException.Conflict e) {
            throw new IllegalStateException("Insufficient stock for one or more items");
        } catch (FeignException e) {
            log.error("Failed to reserve stock with inventory-service", e);
            throw new UpstreamServiceException("Inventory service unavailable, please retry");
        }
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

        if (order.getStatus() == OrderStatus.SHIPPED
                || order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Order " + id + " cannot be cancelled from status " + order.getStatus());
        }

        boolean wasPaid = order.getStatus() == OrderStatus.PAID;
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Order {} cancelled", id);
        publishCancelled(order, wasPaid);
    }

    @Transactional
    public void handlePaymentFailed(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Order {} cancelled due to payment failure", orderId);
        publishCancelled(order, false);
    }

    /**
     * Called by {@link OrderExpiryScheduler} for orders that looked PENDING and stale at query
     * time. Re-checks status fresh, inside its own transaction, immediately before mutating - if
     * a payment webhook legitimately updated this exact order in the meantime, the optimistic
     * @Version check fails this whole transaction with {@link ObjectOptimisticLockingFailureException}
     * instead of silently overwriting that update. That exception is intentionally left to
     * propagate (rather than caught here) since Hibernate only guarantees it surfaces reliably
     * at flush/commit time, which for a @Transactional method happens after the method body
     * returns - catching it in the caller, one order at a time, is what actually works.
     */
    @Transactional
    public void expireIfStillPending(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Order {} expired - no payment received in time, releasing reserved stock", orderId);
        publishCancelled(order, false);
    }

    private void publishCancelled(Order order, boolean wasPaid) {
        orderEventProducer.sendOrderCancelled(OrderCancelledEvent.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .paid(wasPaid)
                .items(order.getItems().stream()
                        .map(item -> OrderCancelledEvent.OrderItemEvent.builder()
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .build());
    }

    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        order.setStatus(status);
        orderRepository.save(order);
        log.info("Order {} status updated to {}", orderId, status);
    }
}