package com.bakaru.inventoryservice.dto;

import com.bakaru.inventoryservice.model.Inventory;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public Inventory toEntity(InventoryRequest request) {
        return Inventory.builder()
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .reserved(0)
                .build();
    }

    public InventoryResponse toResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(inventory.getProductId())
                .quantity(inventory.getQuantity())
                .reserved(inventory.getReserved())
                .available(inventory.getQuantity() - inventory.getReserved())
                .build();
    }
}