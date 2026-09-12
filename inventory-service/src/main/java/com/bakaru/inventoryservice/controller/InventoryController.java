package com.bakaru.inventoryservice.controller;

import com.bakaru.inventoryservice.dto.InventoryRequest;
import com.bakaru.inventoryservice.dto.InventoryResponse;
import com.bakaru.common.dto.ReservationLine;
import com.bakaru.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getByProductId(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getByProductId(productId));
    }

    @PostMapping
    public ResponseEntity<InventoryResponse> createInventory(
            @Valid @RequestBody InventoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.createInventory(request));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<InventoryResponse> updateStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        return ResponseEntity.ok(inventoryService.updateStock(productId, quantity));
    }


    @GetMapping("/batch")
    public ResponseEntity<List<InventoryResponse>> getByProductIds(
            @RequestParam List<Long> productIds) {
        return ResponseEntity.ok(inventoryService.getByProductIds(productIds));
    }

    @PostMapping("/reserve")
    public ResponseEntity<Void> reserve(
            @Valid @NotEmpty @RequestBody List<@Valid ReservationLine> items) {
        inventoryService.reserveBatch(items);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(
            @Valid @NotEmpty @RequestBody List<@Valid ReservationLine> items) {
        inventoryService.releaseBatch(items);
        return ResponseEntity.ok().build();
    }
}