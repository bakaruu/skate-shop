package com.bakaru.productservice.dto;

import com.bakaru.productservice.model.Category;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private String brand;
    private Category category;
    private BigDecimal price;
    private String description;
    private String imageUrl;
    private Double width;
    private Boolean active;
}