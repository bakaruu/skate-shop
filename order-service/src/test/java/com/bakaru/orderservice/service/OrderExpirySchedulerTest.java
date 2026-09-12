package com.bakaru.orderservice.service;

import com.bakaru.orderservice.model.Order;
import com.bakaru.orderservice.model.OrderStatus;
import com.bakaru.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirySchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    private OrderExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OrderExpiryScheduler(orderRepository, orderService);
        ReflectionTestUtils.setField(scheduler, "reservationTtlMinutes", 30L);
    }

    @Test
    void expireStaleOrders_whenNoStaleOrders_doesNothing() {
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any())).thenReturn(List.of());

        scheduler.expireStaleOrders();

        verifyNoInteractions(orderService);
    }

    @Test
    void expireStaleOrders_callsExpireForEachStaleOrder() {
        Order first = Order.builder().id(1L).status(OrderStatus.PENDING).createdAt(LocalDateTime.now().minusHours(1)).build();
        Order second = Order.builder().id(2L).status(OrderStatus.PENDING).createdAt(LocalDateTime.now().minusHours(2)).build();
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any())).thenReturn(List.of(first, second));

        scheduler.expireStaleOrders();

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(orderService, times(2)).expireIfStillPending(idCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(idCaptor.getAllValues()).containsExactly(1L, 2L);
    }

    @Test
    void expireStaleOrders_whenOneOrderFails_stillProcessesTheRest() {
        Order first = Order.builder().id(1L).status(OrderStatus.PENDING).createdAt(LocalDateTime.now().minusHours(1)).build();
        Order second = Order.builder().id(2L).status(OrderStatus.PENDING).createdAt(LocalDateTime.now().minusHours(2)).build();
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any())).thenReturn(List.of(first, second));
        doThrow(new ObjectOptimisticLockingFailureException(Order.class, 1L)).when(orderService).expireIfStillPending(1L);

        scheduler.expireStaleOrders();

        verify(orderService).expireIfStillPending(1L);
        verify(orderService).expireIfStillPending(2L);
    }
}
