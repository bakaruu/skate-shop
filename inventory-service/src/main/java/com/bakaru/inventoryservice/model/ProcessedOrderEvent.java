package com.bakaru.inventoryservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Idempotency ledger for Kafka events consumed by this service, keyed by (topic, orderId).
 * Kafka's at-least-once delivery means the same message can be redelivered (e.g. after a
 * consumer restart before the offset was committed); recording that an (topic, orderId) pair
 * was already handled lets the consumer skip a redelivered message instead of double-applying
 * a stock mutation.
 */
@Entity
@Table(name = "processed_order_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"topic", "order_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedOrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
