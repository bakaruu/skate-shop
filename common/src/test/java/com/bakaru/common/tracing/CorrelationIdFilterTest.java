package com.bakaru.common.tracing;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void whenNoIncomingHeader_mintsANewIdAndPutsItInMdcDuringTheChainCall() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
        verify(chain).doFilter(any(), any());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY))
                .as("MDC must be cleared again once the request has finished")
                .isNull();
    }

    @Test
    void whenIncomingHeaderPresent_reusesItInsteadOfMintingANewOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.addHeader(CorrelationIdFilter.HEADER, "existing-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("existing-id-123");
    }

    @Test
    void theWrappedRequestPassedDownTheChainExposesTheMintedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenByDownstream = new String[1];
        FilterChain chain = (req, res) -> seenByDownstream[0] = ((jakarta.servlet.http.HttpServletRequest) req).getHeader(CorrelationIdFilter.HEADER);

        filter.doFilter(request, response, chain);

        assertThat(seenByDownstream[0])
                .as("downstream code (e.g. the gateway's own proxying) must see the freshly-minted id as if it had always been on the request")
                .isNotBlank()
                .isEqualTo(response.getHeader(CorrelationIdFilter.HEADER));
    }
}
