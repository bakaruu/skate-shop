package com.bakaru.common.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

/**
 * Reads {@value #HEADER} off the incoming request, or mints a new one if this is the first
 * hop (e.g. the browser calling the gateway, which never sends one), and puts it in MDC so
 * every log line for this request - across every service it touches - can be tied back
 * together. The request is wrapped so the id also appears in {@code getHeader}/
 * {@code getHeaderNames}, which matters specifically at the gateway: its proxying reads
 * headers straight off the incoming request to build the outgoing one, and a plain
 * {@code HttpServletRequest} has no way to add a header that wasn't actually sent - only the
 * wrapper lets a freshly-minted id ride along to the downstream service.
 * Ordered first (highest precedence) so every other filter - including one that might reject
 * the request outright, like the gateway's RateLimitFilter - still runs with a correlation id
 * already in MDC and already set on the response.
 * See {@link FeignCorrelationIdInterceptor} and {@link CorrelationIdRecordInterceptor} for how
 * the same id survives a synchronous Feign call or an asynchronous Kafka hop - and their
 * javadoc for the one case (Feign calls guarded by a Resilience4j TimeLimiter) where it doesn't.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String existing = request.getHeader(HEADER);
        String correlationId = (existing == null || existing.isBlank()) ? UUID.randomUUID().toString() : existing;

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(new CorrelationIdHeaderRequest(request, correlationId), response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static final class CorrelationIdHeaderRequest extends HttpServletRequestWrapper {
        private final String correlationId;

        CorrelationIdHeaderRequest(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String name) {
            return HEADER.equalsIgnoreCase(name) ? correlationId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(Collections.singletonList(correlationId))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            if (names.stream().noneMatch(HEADER::equalsIgnoreCase)) {
                names.add(HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
