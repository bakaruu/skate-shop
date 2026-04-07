package com.bakaru.inventoryservice.repository;

import com.bakaru.inventoryservice.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);
    boolean existsByProductId(Long productId);

    List<Inventory> findByProductIdIn(List<Long> productIds);
}