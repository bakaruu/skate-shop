package com.bakaru.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A per-client-IP token bucket applied to every /api/** request before it reaches routing,
 * so a caller hammering the gateway gets a fast 429 instead of a circuit breaker eventually
 * tripping for everyone. Buckets live in a plain in-memory map, which is enough for this
 * project's single gateway instance - a multi-instance deployment would need a shared store
 * (e.g. Redis) instead, since each instance would otherwise track its own counters.
 * Ordered explicitly after CorrelationIdFilter, so a rejected (429) request still gets a
 * correlation id on its response and in the log line for the rejection itself.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.capacity:60}")
    private int capacity;

    @Value("${app.rate-limit.refill-tokens:60}")
    private int refillTokens;

    @Value("${app.rate-limit.refill-seconds:60}")
    private int refillSeconds;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Bucket bucket = buckets.computeIfAbsent(clientKey(request), key -> newBucket());

        if (bucket.tryConsume(1)) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", 429);
        body.put("error", "rate_limit_exceeded");
        body.put("message", "Too many requests, please slow down and try again shortly");
        objectMapper.writeValue(response.getWriter(), body);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(refillTokens, Duration.ofSeconds(refillSeconds)))
                .build();
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
