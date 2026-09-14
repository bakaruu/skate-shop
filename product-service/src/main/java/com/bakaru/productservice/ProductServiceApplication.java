package com.bakaru.productservice;

import com.bakaru.common.GlobalExceptionHandler;
import com.bakaru.common.tracing.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableCaching
@OpenAPIDefinition(
        info = @Info(
                title = "Skate Shop · Product Service",
                version = "1.0.0",
                description = "Product catalog: listing with combined filters, brands, and the batch endpoint "
                        + "order-service uses to fetch authoritative prices. Reads are cached in Redis."
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Through the API Gateway"),
                @Server(url = "http://localhost:8081", description = "Direct to the service")
        }
)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
public class ProductServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProductServiceApplication.class, args);
	}

}
