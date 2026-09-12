package com.bakaru.paymentservice.config;

import com.bakaru.common.ErrorResponseFactory;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Handles the one exception type specific to this service; every other exception is mapped by
 * the shared {@link com.bakaru.common.GlobalExceptionHandler} imported on the application class.
 */
@RestControllerAdvice
public class StripeExceptionHandler {

    @ExceptionHandler(StripeException.class)
    public ResponseEntity<Map<String, Object>> handleStripeException(StripeException ex) {
        return ErrorResponseFactory.build(HttpStatus.BAD_GATEWAY, "Stripe error: " + ex.getMessage());
    }
}
