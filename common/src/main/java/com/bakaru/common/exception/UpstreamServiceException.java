package com.bakaru.common.exception;

/**
 * Thrown when a call to another service (via Feign) fails because that service is down,
 * timed out, or otherwise unreachable - as opposed to a genuine business rule violation.
 * Mapped to 503 by {@link com.bakaru.common.GlobalExceptionHandler}.
 */
public class UpstreamServiceException extends RuntimeException {
    public UpstreamServiceException(String message) {
        super(message);
    }
}
