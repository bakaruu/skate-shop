package com.bakaru.inventoryservice;

import com.bakaru.common.GlobalExceptionHandler;
import com.bakaru.common.tracing.CorrelationIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
@Import({GlobalExceptionHandler.class, CorrelationIdFilter.class})
public class InventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryServiceApplication.class, args);
	}

}
