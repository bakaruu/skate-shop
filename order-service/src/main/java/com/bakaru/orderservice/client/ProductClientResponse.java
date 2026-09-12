package com.bakaru.orderservice.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductClientResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private Boolean active;
}
