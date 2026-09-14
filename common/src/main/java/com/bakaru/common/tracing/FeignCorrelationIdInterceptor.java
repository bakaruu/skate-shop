package com.bakaru.common.tracing;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/**
 * Copies the current request's correlation id (put in MDC by {@link CorrelationIdFilter}) onto
 * every outgoing Feign call, so a synchronous hop between services keeps the same id instead
 * of the downstream service minting its own.
 *
 * <p><b>Known gap:</b> this only works when the Feign call runs on the same thread that set
 * MDC. When {@code feign.circuitbreaker.enabled=true} is combined with a Resilience4j
 * {@code TimeLimiter} (as order-service and payment-service both configure, to enforce a hard
 * timeout on calls to other services), Spring Cloud OpenFeign's
 * {@code FeignCircuitBreakerInvocationHandler} submits the call - interceptors included - to a
 * separate thread pool so it can be interrupted on timeout. MDC is thread-local, so it does not
 * follow the call there: this interceptor still runs, but {@code MDC.get(...)} returns
 * {@code null} on that thread, and the header is silently omitted (see
 * <a href="https://github.com/spring-cloud/spring-cloud-openfeign/issues/949">spring-cloud-openfeign#949</a>).
 * In practice this means the id reliably reaches every HTTP entry point and every Kafka
 * consumer, but is lost on the specific hops that go through a TimeLimiter-guarded Feign
 * client. Fixing it properly means giving Resilience4j's executor an MDC-propagating
 * decorator - a real change, not attempted here so as not to touch working circuit-breaker
 * configuration while just adding tracing.
 */
public class FeignCorrelationIdInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            template.header(CorrelationIdFilter.HEADER, correlationId);
        }
    }
}
