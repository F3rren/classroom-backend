package com.prenotazioni.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prenotazioni.dto.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * A refusal at filter level (no token, or an invalid one) on protected routes.
 *
 * Without this, Spring Security would write a generic or empty 401 body instead of the full
 * JSON the application uses everywhere, because this point comes BEFORE the dispatch to the
 * controller: GlobalExceptionHandler has no way of catching it.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String sessionId = "AUTH_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiEnvelope<Void> body = ApiEnvelope.error(
                "UNAUTHORIZED",
                "Autenticazione richiesta",
                "Devi effettuare il login per accedere a questa funzionalità.",
                sessionId
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
