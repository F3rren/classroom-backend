package com.classroom.config;

import com.classroom.dto.ApiEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Writes the envelope straight onto the response, for the two refusals that happen before
 * any controller exists to return one.
 *
 * GlobalExceptionHandler cannot serve them: ApiAuthenticationEntryPoint and
 * ApiAccessDeniedHandler run inside the security filter chain, before the dispatch that
 * @RestControllerAdvice hangs off. So the same four lines - status, content type, envelope,
 * write - were written twice, once in each, each with an ObjectMapper of its own.
 *
 * It is here and not in dto/ because it is the servlet-side counterpart of that package: it
 * knows about HttpServletResponse, which ApiEnvelope deliberately does not.
 */
final class EnvelopeWriter {

    /**
     * One instance, shared. ObjectMapper is thread-safe once configured, and the two
     * handlers were each building their own to serialise the same class.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EnvelopeWriter() {
    }

    /** Sends the envelope with that status, as JSON. */
    static void write(HttpServletResponse response, HttpStatus status, ApiEnvelope<Void> body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MAPPER.writeValue(response.getWriter(), body);
    }
}
