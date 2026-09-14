package com.bakaru.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(new ObjectMapper());
        ReflectionTestUtils.setField(filter, "capacity", 3);
        ReflectionTestUtils.setField(filter, "refillTokens", 3);
        ReflectionTestUtils.setField(filter, "refillSeconds", 60);
    }

    @Test
    void requestsWithinCapacity_areAllowedThrough() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = apiRequest("1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void requestsBeyondCapacity_areRejectedWith429() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        String ip = "5.6.7.8";

        for (int i = 0; i < 3; i++) {
            filter.doFilter(apiRequest(ip), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(apiRequest(ip), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("rate_limit_exceeded");
    }

    @Test
    void differentClientIps_haveIndependentBuckets() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 3; i++) {
            filter.doFilter(apiRequest("1.1.1.1"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse responseForOtherClient = new MockHttpServletResponse();
        filter.doFilter(apiRequest("2.2.2.2"), responseForOtherClient, chain);

        assertThat(responseForOtherClient.getStatus()).isNotEqualTo(429);
    }

    @Test
    void nonApiPaths_areNeverRateLimited() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    private MockHttpServletRequest apiRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.setRemoteAddr(ip);
        return request;
    }
}
