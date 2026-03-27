package com.bakaru.inventoryservice.service;

import com.bakaru.inventoryservice.dto.InventoryMapper;
import com.bakaru.inventoryservice.dto.InventoryRequest;
import com.bakaru.inventoryservice.dto.InventoryResponse;
import com.bakaru.inventoryservice.model.Inventory;
import com.bakaru.inventoryservice.repository.InventoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;

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
    public void decreaseStock(Long productId, Integer quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Inventory not found for product: " + productId));

        int available = inventory.getQuantity() - inventory.getReserved();
        if (available < quantity) {
            throw new IllegalStateException(
                    "Insufficient stock for product: " + productId);
        }
        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);
        log.info("Stock decreased for product {} by {}", productId, quantity);
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
}