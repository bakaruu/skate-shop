package com.bakaru.productservice.integration;

import com.bakaru.productservice.dto.ProductResponse;
import com.bakaru.productservice.model.Category;
import com.bakaru.productservice.model.Product;
import com.bakaru.productservice.repository.ProductRepository;
import com.bakaru.productservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Redis caching re-enabled on ProductService actually caches, against a real Postgres
 * and a real Redis: after the first call populates the cache, the underlying row is modified
 * directly (bypassing the service layer, so no @CacheEvict fires) - a second call must still
 * return the stale cached value instead of re-querying Postgres, and evicting the cache must
 * make the fresh value visible again.
 */
@Testcontainers
@SpringBootTest
class ProductCachingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    private Long productId;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        var cache = cacheManager.getCache("products");
        if (cache != null) {
            cache.clear();
        }

        Product saved = productRepository.save(Product.builder()
                .name("Cached Deck")
                .brand("TestBrand")
                .category(Category.DECK)
                .price(new BigDecimal("99.99"))
                .active(true)
                .build());
        productId = saved.getId();
    }

    @Test
    void getProductById_calledTwice_secondCallServesStaleCachedValue() {
        ProductResponse first = productService.getProductById(productId);
        assertThat(first.getName()).isEqualTo("Cached Deck");

        // bypass the service layer (and its @CacheEvict) to change the row directly
        Product product = productRepository.findById(productId).orElseThrow();
        product.setName("Changed Directly In DB");
        productRepository.save(product);

        ProductResponse second = productService.getProductById(productId);

        assertThat(second.getName())
                .as("a cache hit should still return the stale name - if this fails with the new name, the cache isn't being used")
                .isEqualTo("Cached Deck");
    }

    @Test
    void updateProduct_evictsCacheSoNextReadIsFresh() {
        productService.getProductById(productId);

        Product product = productRepository.findById(productId).orElseThrow();
        product.setName("Changed Directly In DB");
        productRepository.save(product);
        assertThat(productService.getProductById(productId).getName()).isEqualTo("Cached Deck");

        productService.updateProduct(productId, com.bakaru.productservice.dto.ProductRequest.builder()
                .name("Updated Via Service")
                .brand("TestBrand")
                .category(Category.DECK)
                .price(new BigDecimal("109.99"))
                .build());

        assertThat(productService.getProductById(productId).getName()).isEqualTo("Updated Via Service");
    }

    /**
     * getProductById's single-object cache entry never exercises a real deserialization round
     * trip failure the way a cached List does: GenericJackson2JsonRedisSerializer can serialize
     * Stream.toList()'s immutable ImmutableCollections$ListN just fine (it only reads elements),
     * but fails to deserialize that exact type back out of Redis on the second (real cache-hit)
     * call - a bug that hid behind the getProductById-only coverage above until it broke the live
     * catalog page. This test forces getAllProducts through a genuine second-call deserialization.
     */
    @Test
    void getAllProducts_calledTwice_secondCallDeserializesFromRedisWithoutError() {
        List<ProductResponse> first = productService.getAllProducts(null, null, null, null, null);
        assertThat(first).hasSize(1);
        assertThat(first.get(0).getName()).isEqualTo("Cached Deck");

        List<ProductResponse> second = productService.getAllProducts(null, null, null, null, null);

        assertThat(second)
                .as("the second call must be served from Redis - if deserialization is broken, this throws instead of returning")
                .hasSize(1);
        assertThat(second.get(0).getName()).isEqualTo("Cached Deck");
    }
}
