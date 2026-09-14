package com.bakaru.orderservice.config;

import com.bakaru.common.tracing.FeignCorrelationIdInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignCorrelationConfig {

    @Bean
    public RequestInterceptor correlationIdRequestInterceptor() {
        return new FeignCorrelationIdInterceptor();
    }
}
