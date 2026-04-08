package com.bakaru.orderservice.service;

import com.bakaru.orderservice.dto.OrderItemRequest;
import com.bakaru.orderservice.dto.OrderMapper;
import com.bakaru.orderservice.dto.OrderRequest;
import com.bakaru.orderservice.dto.OrderResponse;
import com.bakaru.orderservice.event.OrderCancelledEvent;
import com.bakaru.orderservice.event.OrderPlacedEvent;
import com.bakaru.orderservice.model.Order;
import com.bakaru.orderservice.model.OrderItem;
import com.bakaru.orderservice.model.OrderStatus;
import com.bakaru.orderservice.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderEventProducer orderEventProducer;

    @InjectMocks
    private OrderService orderService;

    private Order order;
    private OrderResponse orderResponse;
    private OrderRequest orderRequest;

    @BeforeEach
    void setUp() {
        OrderItem item = OrderItem.builder()
                .id(1L)
                .productId(10L)
                .quantity(2)
                .unitPrice(new BigDecimal("79.99"))
                .build();

        order = Order.builder()
                .id(1L)
                .customerId(100L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("159.98"))
                .createdAt(LocalDateTime.now())
                .items(List.of(item))
                .build();
        item.setOrder(order);

        orderResponse = OrderResponse.builder()
                .id(1L)
                .customerId(100L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("159.98"))
                .build();

        orderRequest = OrderRequest.builder()
                .customerId(100L)
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L)
                        .quantity(2)
                        .unitPrice(new BigDecimal("79.99"))
                        .build()))
                .build();
    }

    @Test
    void createOrder_savesOrderAndPublishesKafkaEvent() {
        when(orderMapper.toEntity(orderRequest)).thenReturn(order);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(orderResponse);

        OrderResponse result = orderService.createOrder(orderRequest);

        assertThat(result.getCustomerId()).isEqualTo(100L);
        verify(orderRepository).save(order);

        ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(orderEventProducer).sendOrderPlaced(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().getCustomerId()).isEqualTo(100L);
    }

    @Test
    void getOrderById_whenExists_returnsResponse() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(orderResponse);

        OrderResponse result = orderService.getOrderById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(orderRepository).findById(1L);
    }

    @Test
    void getOrderById_whenNotExists_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getOrdersByCustomer_returnsListForCustomer() {
        when(orderRepository.findByCustomerId(100L)).thenReturn(List.of(order));
        when(orderMapper.toResponse(order)).thenReturn(orderResponse);

        List<OrderResponse> result = orderService.getOrdersByCustomer(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomerId()).isEqualTo(100L);
    }

    @Test
    void updateStatus_whenExists_updatesAndReturns() {
        OrderResponse paidResponse = OrderResponse.builder()
                .id(1L).customerId(100L).status(OrderStatus.PAID).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(paidResponse);

        OrderResponse result = orderService.updateStatus(1L, OrderStatus.PAID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void updateStatus_whenNotExists_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateStatus(99L, OrderStatus.PAID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_setsStatusCancelledAndPublishesEvent() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);

        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventProducer).sendOrderCancelled(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().getCustomerId()).isEqualTo(100L);
    }

    @Test
    void cancelOrder_whenNotExists_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(99L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(orderEventProducer, never()).sendOrderCancelled(any());
    }

    @Test
    void updateOrderStatus_updatesStatusFromKafkaConsumer() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.updateOrderStatus(1L, OrderStatus.PAID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).save(order);
    }
}
