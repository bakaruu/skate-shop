package com.bakaru.inventoryservice.service;

import com.bakaru.inventoryservice.dto.InventoryMapper;
import com.bakaru.inventoryservice.dto.InventoryRequest;
import com.bakaru.inventoryservice.dto.InventoryResponse;
import com.bakaru.inventoryservice.model.Inventory;
import com.bakaru.inventoryservice.repository.InventoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory inventory;
    private InventoryResponse inventoryResponse;

    @BeforeEach
    void setUp() {
        inventory = Inventory.builder()
                .id(1L)
                .productId(10L)
                .quantity(20)
                .reserved(0)
                .build();

        inventoryResponse = InventoryResponse.builder()
                .id(1L)
                .productId(10L)
                .quantity(20)
                .reserved(0)
                .available(20)
                .build();
    }

    @Test
    void getByProductId_whenExists_returnsResponse() {
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        InventoryResponse result = inventoryService.getByProductId(10L);

        assertThat(result.getProductId()).isEqualTo(10L);
        assertThat(result.getQuantity()).isEqualTo(20);
    }

    @Test
    void getByProductId_whenNotExists_throwsEntityNotFoundException() {
        when(inventoryRepository.findByProductId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getByProductId(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createInventory_whenProductNotExists_createsSuccessfully() {
        InventoryRequest request = InventoryRequest.builder()
                .productId(10L).quantity(20).build();

        when(inventoryRepository.existsByProductId(10L)).thenReturn(false);
        when(inventoryMapper.toEntity(request)).thenReturn(inventory);
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        InventoryResponse result = inventoryService.createInventory(request);

        assertThat(result.getProductId()).isEqualTo(10L);
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void createInventory_whenAlreadyExists_throwsIllegalStateException() {
        InventoryRequest request = InventoryRequest.builder()
                .productId(10L).quantity(20).build();

        when(inventoryRepository.existsByProductId(10L)).thenReturn(true);

        assertThatThrownBy(() -> inventoryService.createInventory(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10");

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void updateStock_whenExists_updatesQuantity() {
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        inventoryService.updateStock(10L, 50);

        assertThat(inventory.getQuantity()).isEqualTo(50);
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void decreaseStock_withSufficientStock_decreasesQuantity() {
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));

        inventoryService.decreaseStock(10L, 5);

        assertThat(inventory.getQuantity()).isEqualTo(15);
        verify(inventoryRepository, atLeastOnce()).save(inventory);
    }

    @Test
    void decreaseStock_withInsufficientStock_throwsIllegalStateException() {
        inventory.setQuantity(2);
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.decreaseStock(10L, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void decreaseStock_whenReachesZero_autoReplenishesToTen() {
        inventory.setQuantity(3);
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));

        inventoryService.decreaseStock(10L, 3);

        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void increaseStock_whenExists_increasesQuantity() {
        when(inventoryRepository.findByProductId(10L)).thenReturn(Optional.of(inventory));

        inventoryService.increaseStock(10L, 10);

        assertThat(inventory.getQuantity()).isEqualTo(30);
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void getByProductIds_returnsListForGivenIds() {
        when(inventoryRepository.findByProductIdIn(List.of(10L, 11L)))
                .thenReturn(List.of(inventory));
        when(inventoryMapper.toResponse(inventory)).thenReturn(inventoryResponse);

        List<InventoryResponse> result = inventoryService.getByProductIds(List.of(10L, 11L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductId()).isEqualTo(10L);
    }
}
