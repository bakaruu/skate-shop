package com.bakaru.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the standard {timestamp, status, message} error envelope used by every service's
 * exception handling, so the response shape only has one definition to change.
 */
public final class ErrorResponseFactory {

    private ErrorResponseFactory() {
    }

    public static ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
