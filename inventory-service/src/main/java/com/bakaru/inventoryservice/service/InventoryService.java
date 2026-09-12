package com.bakaru.inventoryservice.service;

import com.bakaru.inventoryservice.dto.InventoryMapper;
import com.bakaru.inventoryservice.dto.InventoryRequest;
import com.bakaru.inventoryservice.dto.InventoryResponse;
import com.bakaru.common.dto.ReservationLine;
import com.bakaru.inventoryservice.model.Inventory;
import com.bakaru.inventoryservice.model.ProcessedOrderEvent;
import com.bakaru.inventoryservice.repository.InventoryRepository;
import com.bakaru.inventoryservice.repository.ProcessedOrderEventRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final ProcessedOrderEventRepository processedOrderEventRepository;

    /**
     * Records that a Kafka event for (topic, orderId) is about to be handled, so a redelivery
     * of the same message can be recognized and skipped. Marks first, before doing the actual
     * stock mutation, so a crash between marking and mutating leaves stock merely understated
     * (safe) rather than risking a double-decrement (which could oversell) on redelivery.
     * Returns false if this (topic, orderId) pair was already processed.
     */
    @Transactional
    public boolean tryMarkProcessed(String topic, Long orderId) {
        if (processedOrderEventRepository.existsByTopicAndOrderId(topic, orderId)) {
            return false;
        }
        processedOrderEventRepository.save(ProcessedOrderEvent.builder()
                .topic(topic)
                .orderId(orderId)
                .processedAt(LocalDateTime.now())
                .build());
        return true;
    }

    public InventoryResponse getByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Inventory not found for product: " + productId));
        return inventoryMapper.toResponse(inventory);
    }

    @Transactional
    public InventoryResponse createInventory(InventoryRequest request) {
        if (inventoryRepository.existsByProductId(request.getProductId())) {
            throw new IllegalStateException(
                    "Inventory already exists for product: " + request.getProductId());
        }
        Inventory inventory = inventoryMapper.toEntity(request);
        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    @Transactional
    public InventoryResponse updateStock(Long productId, Integer quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Inventory not found for product: " + productId));
        inventory.setQuantity(quantity);
        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    @Transactional
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void decreaseStock(Long productId, Integer quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Inventory not found for product: " + productId));

        // This is called for an order whose stock was already reserved (reserved holds this
        // order's quantity), so we only need enough raw quantity to fulfil it - checking against
        // "available" here would incorrectly count OTHER orders' concurrent reservations against it.
        if (inventory.getQuantity() < quantity) {
            throw new IllegalStateException(
                    "Insufficient stock for product: " + productId);
        }
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setReserved(Math.max(0, inventory.getReserved() - quantity));
        inventoryRepository.save(inventory);
        log.info("Stock decreased for product {} by {}", productId, quantity);

        if (inventory.getQuantity() <= 0) {
            inventory.setQuantity(10);
            inventoryRepository.save(inventory);
            log.info("Stock auto-replenished for product {} to 10", productId);
        }
    }

    @Transactional
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void reserveBatch(List<ReservationLine> lines) {
        Map<Long, Inventory> byProductId = fetchByProductIds(lines);

        for (ReservationLine line : lines) {
            Inventory inventory = requireInventory(byProductId, line.getProductId());
            int available = inventory.getQuantity() - inventory.getReserved();
            if (available < line.getQuantity()) {
                throw new IllegalStateException(
                        "Insufficient stock for product: " + line.getProductId());
            }
            inventory.setReserved(inventory.getReserved() + line.getQuantity());
        }

        inventoryRepository.saveAll(List.copyOf(byProductId.values()));
        log.info("Reserved stock for {} line(s)", lines.size());
    }

    @Transactional
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void releaseBatch(List<ReservationLine> lines) {
        Map<Long, Inventory> byProductId = fetchByProductIds(lines);

        for (ReservationLine line : lines) {
            Inventory inventory = requireInventory(byProductId, line.getProductId());
            inventory.setReserved(Math.max(0, inventory.getReserved() - line.getQuantity()));
        }

        inventoryRepository.saveAll(List.copyOf(byProductId.values()));
        log.info("Released reserved stock for {} line(s)", lines.size());
    }

    /** One SELECT for the whole batch instead of one per line. */
    private Map<Long, Inventory> fetchByProductIds(List<ReservationLine> lines) {
        List<Long> productIds = lines.stream().map(ReservationLine::getProductId).distinct().toList();
        return inventoryRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
    }

    private Inventory requireInventory(Map<Long, Inventory> byProductId, Long productId) {
        Inventory inventory = byProductId.get(productId);
        if (inventory == null) {
            throw new EntityNotFoundException("Inventory not found for product: " + productId);
        }
        return inventory;
    }

    @Transactional
    public void increaseStock(Long productId, Integer quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Inventory not found for product: " + productId));
        inventory.setQuantity(inventory.getQuantity() + quantity);
        inventoryRepository.save(inventory);
        log.info("Stock increased for product {} by {}", productId, quantity);
    }


    public List<InventoryResponse> getByProductIds(List<Long> productIds) {
        return inventoryRepository.findByProductIdIn(productIds)
                .stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }
}