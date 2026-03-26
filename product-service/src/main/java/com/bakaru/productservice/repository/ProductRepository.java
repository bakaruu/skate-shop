package com.bakaru.productservice.repository;

import com.bakaru.productservice.model.Category;
import com.bakaru.productservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    boolean existsByNameAndBrand(String name, String brand);
}