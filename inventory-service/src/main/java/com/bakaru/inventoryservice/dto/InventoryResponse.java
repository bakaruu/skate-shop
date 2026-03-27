package com.bakaru.inventoryservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryResponse {

    private Long id;
    private Long productId;
    private Integer quantity;
    private Integer reserved;
    private Integer available;
}