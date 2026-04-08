package com.bakaru.productservice.service;

import com.bakaru.productservice.dto.ProductMapper;
import com.bakaru.productservice.dto.ProductRequest;
import com.bakaru.productservice.dto.ProductResponse;
import com.bakaru.productservice.model.Category;
import com.bakaru.productservice.model.Product;
import com.bakaru.productservice.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    private Product product;
    private ProductResponse productResponse;
    private ProductRequest productRequest;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L)
                .name("Element Deck")
                .brand("Element")
                .category(Category.DECK)
                .price(new BigDecimal("79.99"))
                .active(true)
                .build();

        productResponse = ProductResponse.builder()
                .id(1L)
                .name("Element Deck")
                .brand("Element")
                .category(Category.DECK)
                .price(new BigDecimal("79.99"))
                .active(true)
                .build();

        productRequest = ProductRequest.builder()
                .name("Element Deck")
                .brand("Element")
                .category(Category.DECK)
                .price(new BigDecimal("79.99"))
                .build();
    }

    @Test
    void getProductById_whenExists_returnsResponse() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(productResponse);

        ProductResponse result = productService.getProductById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Element Deck");
        verify(productRepository).findById(1L);
    }

    @Test
    void getProductById_whenNotExists_throwsEntityNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createProduct_savesAndReturnsResponse() {
        when(productMapper.toEntity(productRequest)).thenReturn(product);
        when(productRepository.save(product)).thenReturn(product);
        when(productMapper.toResponse(product)).thenReturn(productResponse);

        ProductResponse result = productService.createProduct(productRequest);

        assertThat(result.getName()).isEqualTo("Element Deck");
        verify(productRepository).save(product);
    }

    @Test
    void updateProduct_whenExists_updatesFields() {
        ProductRequest updateRequest = ProductRequest.builder()
                .name("Updated Deck")
                .brand("Santa Cruz")
                .category(Category.DECK)
                .price(new BigDecimal("89.99"))
                .build();

        ProductResponse updatedResponse = ProductResponse.builder()
                .id(1L)
                .name("Updated Deck")
                .brand("Santa Cruz")
                .category(Category.DECK)
                .price(new BigDecimal("89.99"))
                .active(true)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toResponse(any(Product.class))).thenReturn(updatedResponse);

        ProductResponse result = productService.updateProduct(1L, updateRequest);

        assertThat(result.getName()).isEqualTo("Updated Deck");
        assertThat(result.getBrand()).isEqualTo("Santa Cruz");
        verify(productRepository).save(product);
    }

    @Test
    void updateProduct_whenNotExists_throwsEntityNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(99L, productRequest))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");

        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteProduct_setsActiveToFalse() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.deleteProduct(1L);

        assertThat(product.getActive()).isFalse();
        verify(productRepository).save(product);
    }

    @Test
    void deleteProduct_whenNotExists_throwsEntityNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(99L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void getAllProducts_returnsFilteredList() {
        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(product));
        when(productMapper.toResponse(product)).thenReturn(productResponse);

        List<ProductResponse> result = productService.getAllProducts(null, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Element Deck");
    }

    @Test
    void getAllBrands_returnsDistinctSortedBrands() {
        Product product2 = Product.builder()
                .id(2L).name("Thunder Trucks").brand("Thunder")
                .category(Category.TRUCKS).price(new BigDecimal("49.99")).active(true).build();
        Product product3 = Product.builder()
                .id(3L).name("Element Wheels").brand("Element")
                .category(Category.WHEELS).price(new BigDecimal("39.99")).active(true).build();
        Product inactiveProduct = Product.builder()
                .id(4L).name("Old Deck").brand("Zoo York")
                .category(Category.DECK).price(new BigDecimal("59.99")).active(false).build();

        when(productRepository.findAll()).thenReturn(List.of(product, product2, product3, inactiveProduct));

        List<String> brands = productService.getAllBrands();

        assertThat(brands).containsExactly("Element", "Thunder");
        assertThat(brands).doesNotContain("Zoo York");
    }
}
