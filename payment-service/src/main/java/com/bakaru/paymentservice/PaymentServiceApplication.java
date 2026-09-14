package com.bakaru.paymentservice;

import com.bakaru.common.GlobalExceptionHandler;
import com.bakaru.common.tracing.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableFeignClients
@OpenAPIDefinition(
        info = @Info(
                title = "Skate Shop · Payment Service",
                version = "1.0.0",
                description = "Stripe Checkout integration. The amount is always derived server-side from the "
                        + "authoritative order, never sent by the client, and the webhook endpoint verifies Stripe's "
                        + "signature and ignores duplicate deliveries."
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Through the API Gateway"),
                @Server(url = "http://localhost:8085", description = "Direct to the service")
        }
)
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

}
