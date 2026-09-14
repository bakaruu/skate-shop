package com.bakaru.orderservice;

import com.bakaru.common.GlobalExceptionHandler;
import com.bakaru.common.tracing.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@OpenAPIDefinition(
        info = @Info(
                title = "Skate Shop · Order Service",
                version = "1.0.0",
                description = "Orchestrates an order: fetches authoritative prices from product-service, reserves "
                        + "stock in inventory-service, then persists and publishes the order. Accepts an optional "
                        + "Idempotency-Key header so a retried request returns the original order instead of a duplicate."
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Through the API Gateway"),
                @Server(url = "http://localhost:8083", description = "Direct to the service")
        }
)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}

}
