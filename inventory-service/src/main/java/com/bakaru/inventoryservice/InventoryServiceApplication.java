package com.bakaru.inventoryservice;

import com.bakaru.common.GlobalExceptionHandler;
import com.bakaru.common.tracing.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
@OpenAPIDefinition(
        info = @Info(
                title = "Skate Shop · Inventory Service",
                version = "1.0.0",
                description = "Stock levels and reservations. /reserve holds stock synchronously when an order is "
                        + "created and /release gives it back if the order is cancelled or expires; concurrent "
                        + "reservations are guarded with optimistic locking and retries."
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Through the API Gateway"),
                @Server(url = "http://localhost:8082", description = "Direct to the service")
        }
)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
public class InventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryServiceApplication.class, args);
	}

}
