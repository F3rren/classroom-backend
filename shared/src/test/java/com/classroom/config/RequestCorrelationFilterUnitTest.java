package com.classroom.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.FilterChain;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter exists for one reason: to give a request ONE id, not one per class that touches
 * it. The tests below pin down the three behaviours that guarantee depends on; if one breaks,
 * the id goes back to being decorative.
 */
class RequestCorrelationFilterUnitTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clean() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void itReusesTheIdReceivedFromTheCaller() throws Exception {
        // This is the point of the whole class in a system of several services: the gateway
        // mints the id and the downstream services inherit it. If a new one were generated
        // here, a round trip between the gateway and the booking service could never be
        // stitched back together.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER, "REQ_DALGATEWAY");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(RequestCorrelationFilter.ATTRIBUTE)).isEqualTo("REQ_DALGATEWAY");
        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo("REQ_DALGATEWAY");
    }

    @Test
    void itMintsOneWhenTheHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String id = (String) request.getAttribute(RequestCorrelationFilter.ATTRIBUTE);
        assertThat(id).isNotBlank();
        // Sending it back serves whoever opens a report: they can quote the id even when the
        // response is a 204, or a body that does not carry it.
        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo(id);
    }

    @Test
    void theControllerAndTheErrorHandlerReadTheSameValue() throws Exception {
        // The regression this class was born to close: two calls to current() inside the
        // same request have to give the same value. They used to be two different
        // generateSessionId() calls, and a failed request appeared in the logs twice, under
        // two keys, with no way to connect them.
        MockHttpServletRequest request = new MockHttpServletRequest();
        String[] reads = new String[2];

        FilterChain insideTheRequest = (req, res) -> {
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes(Objects.requireNonNull((MockHttpServletRequest) req)));
            reads[0] = RequestCorrelationFilter.current();   // the controller
            reads[1] = RequestCorrelationFilter.current();   // the error handler
        };

        filter.doFilter(request, new MockHttpServletResponse(), insideTheRequest);

        assertThat(reads[0]).isNotBlank().isEqualTo(reads[1]);
    }

    @Test
    void itFallsBackOutsideAnHttpRequest() {
        // A message consumer or a scheduled job has no request. A disconnected id beats a
        // null that ends up in the response printed as the string "null".
        assertThat(RequestCorrelationFilter.current()).isNotBlank();
    }

    @Test
    void applyToMdcUsesTheReceivedId() {
        // The half of the chain that does not go through HTTP: an AMQP listener runs on a
        // thread of its own, outside any request, and without this the notification created
        // from an event would appear in the logs disconnected from the cancellation that
        // caused it.
        RequestCorrelationFilter.applyToMdc("REQ_DALLEVENTO");

        assertThat(MDC.get("requestId")).isEqualTo("REQ_DALLEVENTO");
    }

    @Test
    void applyToMdcFallsBackToAGeneratedOneWhenNoneArrives() {
        // A message published before the header existed has to keep being consumed: absent
        // is not an error, and "null" in the logs would be worse.
        RequestCorrelationFilter.applyToMdc(null);
        assertThat(MDC.get("requestId")).isNotBlank().isNotEqualTo("null");

        RequestCorrelationFilter.applyToMdc("   ");
        assertThat(MDC.get("requestId")).isNotBlank().doesNotContain(" ");
    }

    @Test
    void clearMdcRemovesTheId() {
        RequestCorrelationFilter.applyToMdc("REQ_QUALCOSA");
        RequestCorrelationFilter.clearMdc();

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void clearMdcRunsAtTheEndOfTheRequest() throws Exception {
        // Threads are reused: an MDC left uncleared would make this request's id show up in
        // the next request's log lines, which is worse than not having one at all.
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get("requestId")).isNull();
    }
}
