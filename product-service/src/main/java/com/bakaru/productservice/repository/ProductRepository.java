package com.bakaru.productservice.repository;

import com.bakaru.productservice.model.Category;
import com.bakaru.productservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    boolean existsByNameAndBrand(String name, String brand);

    List<Product> findByIdIn(List<Long> ids);
}