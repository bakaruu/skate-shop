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
import com.bakaru.orderservice.model.OrderItem;
import com.bakaru.orderservice.model.OrderStatus;
import com.bakaru.orderservice.repository.OrderRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private OrderService orderService;

    private Order order;
    private OrderResponse orderResponse;
    private OrderRequest orderRequest;
    private ProductClientResponse productClientResponse;

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
                        .build()))
                .build();

        productClientResponse = new ProductClientResponse(10L, "Deck", new BigDecimal("79.99"), true);
    }

    @Test
    void createOrder_savesOrderAndPublishesKafkaEvent() {
        when(productClient.getByIds(List.of(10L))).thenReturn(List.of(productClientResponse));
        when(orderMapper.toEntity(eq(orderRequest), any())).thenReturn(order);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(orderResponse);

        OrderResponse result = orderService.createOrder(orderRequest);

        assertThat(result.getCustomerId()).isEqualTo(100L);
        verify(inventoryClient).reserve(List.of(new ReservationLine(10L, 2)));
        verify(orderRepository).save(order);

        ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(orderEventProducer).sendOrderPlaced(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().getCustomerId()).isEqualTo(100L);
    }

    @Test
    void createOrder_passesAuthoritativePricesToMapper() {
        when(productClient.getByIds(List.of(10L))).thenReturn(List.of(productClientResponse));
        when(orderMapper.toEntity(eq(orderRequest), any())).thenReturn(order);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(orderResponse);

        orderService.createOrder(orderRequest);

        ArgumentCaptor<Map<Long, BigDecimal>> pricesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(orderMapper).toEntity(eq(orderRequest), pricesCaptor.capture());
        assertThat(pricesCaptor.getValue()).containsEntry(10L, new BigDecimal("79.99"));
    }

    @Test
    void createOrder_whenProductUnknown_throwsIllegalArgumentException() {
        when(productClient.getByIds(List.of(10L))).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown product id");

        verifyNoInteractions(inventoryClient);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_whenProductInactive_throwsIllegalArgumentException() {
        ProductClientResponse inactive = new ProductClientResponse(10L, "Deck", new BigDecimal("79.99"), false);
        when(productClient.getByIds(List.of(10L))).thenReturn(List.of(inactive));

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");

        verifyNoInteractions(inventoryClient);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_whenProductServiceDown_throwsUpstreamServiceException() {
        when(productClient.getByIds(List.of(10L))).thenThrow(feignException(500));

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(UpstreamServiceException.class);

        verifyNoInteractions(inventoryClient);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_whenInsufficientStock_throwsIllegalStateExceptionAndDoesNotPersist() {
        when(productClient.getByIds(List.of(10L))).thenReturn(List.of(productClientResponse));
        doThrow(feignException(409)).when(inventoryClient).reserve(any());

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).sendOrderPlaced(any());
    }

    @Test
    void createOrder_whenPersistenceFailsAfterReservation_releasesStock() {
        when(productClient.getByIds(List.of(10L))).thenReturn(List.of(productClientResponse));
        when(orderMapper.toEntity(eq(orderRequest), any())).thenReturn(order);
        when(orderRepository.save(order)).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(RuntimeException.class);

        verify(inventoryClient).reserve(List.of(new ReservationLine(10L, 2)));
        verify(inventoryClient).release(List.of(new ReservationLine(10L, 2)));
        verify(orderEventProducer, never()).sendOrderPlaced(any());
    }

    private FeignException feignException(int status) {
        Request request = Request.create(Request.HttpMethod.POST, "/api/inventory/reserve",
                Map.of(), Request.Body.empty(), new RequestTemplate());
        byte[] body = "error".getBytes(StandardCharsets.UTF_8);
        return FeignException.errorStatus("reserve", feign.Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .body(body)
                .build());
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
    void cancelOrder_whenPending_setsStatusCancelledAndPublishesEventNotPaid() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);

        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventProducer).sendOrderCancelled(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().getCustomerId()).isEqualTo(100L);
        assertThat(eventCaptor.getValue().isPaid()).isFalse();
        assertThat(eventCaptor.getValue().getItems()).hasSize(1);
        assertThat(eventCaptor.getValue().getItems().get(0).getProductId()).isEqualTo(10L);
    }

    @Test
    void cancelOrder_whenPaid_publishesEventMarkedPaidSoStockGetsRestocked() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventProducer).sendOrderCancelled(eventCaptor.capture());
        assertThat(eventCaptor.getValue().isPaid())
                .as("a paid order's cancellation must tell inventory-service to restock quantity, not just release a reservation")
                .isTrue();
    }

    @Test
    void cancelOrder_whenAlreadyShipped_throwsIllegalStateExceptionAndPublishesNothing() {
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(IllegalStateException.class);

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).sendOrderCancelled(any());
    }

    @Test
    void cancelOrder_whenAlreadyDelivered_throwsIllegalStateException() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(IllegalStateException.class);

        verify(orderEventProducer, never()).sendOrderCancelled(any());
    }

    @Test
    void cancelOrder_whenAlreadyCancelled_throwsIllegalStateException() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(IllegalStateException.class);

        verify(orderEventProducer, never()).sendOrderCancelled(any());
    }

    @Test
    void cancelOrder_whenNotExists_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(99L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(orderEventProducer, never()).sendOrderCancelled(any());
    }

    @Test
    void handlePaymentFailed_cancelsOrderAndPublishesEventNotPaid() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.handlePaymentFailed(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventProducer).sendOrderCancelled(eventCaptor.capture());
        assertThat(eventCaptor.getValue().isPaid())
                .as("a failed payment means the order was never actually paid, so this should only release the reservation")
                .isFalse();
    }

    @Test
    void handlePaymentFailed_whenNotExists_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.handlePaymentFailed(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateOrderStatus_updatesStatusFromKafkaConsumer() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.updateOrderStatus(1L, OrderStatus.PAID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).save(order);
    }

    @Test
    void expireIfStillPending_whenStillPending_cancelsAndPublishesEventNotPaid() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.expireIfStillPending(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);

        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventProducer).sendOrderCancelled(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().isPaid())
                .as("an abandoned checkout was never paid, so this should only release the reservation")
                .isFalse();
    }

    @Test
    void expireIfStillPending_whenAlreadyPaid_doesNothing() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.expireIfStillPending(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).sendOrderCancelled(any());
    }

    @Test
    void expireIfStillPending_whenAlreadyCancelled_doesNothing() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.expireIfStillPending(1L);

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).sendOrderCancelled(any());
    }

    @Test
    void expireIfStillPending_whenNotFound_doesNothing() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        orderService.expireIfStillPending(99L);

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).sendOrderCancelled(any());
    }
}
