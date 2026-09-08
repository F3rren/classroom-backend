package com.classroom.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * One id per request, shared by everything that handles it.
 *
 * Every class used to generate its own: seven implementations with six different prefixes
 * (ADM_, AUTH_, ME_, S, R, ERR_). The practical consequence was that a single request ended
 * up in the logs under TWO distinct ids - the controller's, and the one
 * GlobalExceptionHandler generated while answering - with no way to connect them. The field
 * that exists to correlate logs could not do it, which is the worst way for a diagnostic
 * tool to fail: it looks like it is working.
 *
 * The id travels between services too. If the request arrives carrying an X-Request-Id
 * header it is reused rather than replaced: that way a round trip across the gateway, the
 * booking service and the notification service is followed with one single key. In a system
 * of several services that is the only way to reconstruct what happened.
 *
 * It also goes into the MDC, so a log pattern can print it on every line without anybody
 * having to pass it around by hand.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = "com.classroom.requestId";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader(HEADER);
        if (id == null || id.isBlank()) {
            id = generate();
        }

        request.setAttribute(ATTRIBUTE, id);
        MDC.put(MDC_KEY, id);
        // Sent back out: whoever made the call can quote it in a report even when the
        // response has no body to carry it in.
        response.setHeader(HEADER, id);

        try {
            chain.doFilter(request, response);
        } finally {
            // Mandatory: threads are reused, and an MDC left uncleared would make one
            // request's id show up in the next request's log lines.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * The id of the request in flight.
     *
     * The fallback covers the cases where there is no HTTP request at all: a message
     * consumer, a scheduled job, a unit test. A disconnected id beats a null that then shows
     * up as the string "null" inside a response.
     */
    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object id = attributes.getRequest().getAttribute(ATTRIBUTE);
            if (id instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return generate();
    }

    /**
     * Puts into the MDC an id that did NOT come from an HTTP request.
     *
     * Message consumers need this: an AMQP listener runs on a thread of its own, outside any
     * request, and without it the event would appear in the logs under an id disconnected
     * from the operation that caused it.
     *
     * A missing value is not an error: a message published before the header existed has to
     * keep being consumed, simply with an id of its own.
     */
    public static void applyToMdc(String id) {
        MDC.put(MDC_KEY, (id == null || id.isBlank()) ? generate() : id);
    }

    /**
     * To be called in a finally: threads are reused, and an MDC left uncleared would make
     * this message's id show up in the next message's log lines.
     */
    public static void clearMdc() {
        MDC.remove(MDC_KEY);
    }

    private static String generate() {
        return "REQ_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
