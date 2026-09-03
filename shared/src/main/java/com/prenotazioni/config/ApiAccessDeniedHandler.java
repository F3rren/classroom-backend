package com.prenotazioni.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prenotazioni.dto.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Rifiuto a livello di filtro per richieste autenticate ma senza i privilegi
 * richiesti dalla regola di SecurityConfig (non da @PreAuthorize, che invece
 * viene intercettato da GlobalExceptionHandler dentro il dispatch del controller).
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        String sessionId = "AUTH_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiEnvelope<Void> body = ApiEnvelope.error(
                "ACCESS_DENIED",
                "Accesso negato",
                "Non hai i permessi necessari per accedere a questa risorsa.",
                sessionId
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
