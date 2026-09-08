package com.classroom.config;

import com.classroom.dto.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * A refusal at filter level (no token, or an invalid one) on protected routes.
 *
 * Without this, Spring Security would write a generic or empty 401 body instead of the full
 * JSON the application uses everywhere, because this point comes BEFORE the dispatch to the
 * controller: GlobalExceptionHandler has no way of catching it.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * RFC 9110 §11.6.1 makes this header mandatory on a 401: it is how a client is told
     * which authentication to attempt. The realm names the protection space; there is one.
     */
    private static final String CHALLENGE = "Bearer realm=\"classroom\"";

    /**
     * The same challenge, for a token that WAS sent and was not good.
     *
     * The distinction is RFC 6750 §3.1's, and it is worth making because the two cases call
     * for opposite actions: without a token you log in, with an expired one you refresh and
     * retry. Told apart by the request rather than by the exception - JwtAuthFilter does not
     * throw on a bad token, it simply leaves the request unauthenticated, so the only
     * evidence that one was offered is the header it arrived with.
     */
    private static final String INVALID_TOKEN_CHALLENGE = CHALLENGE
            + ", error=\"invalid_token\", error_description=\"The access token is expired or invalid\"";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challengeFor(request));
        ApiEnvelope<Void> body = ApiEnvelope.error(
                "UNAUTHORIZED",
                "Autenticazione richiesta",
                "Devi effettuare il login per accedere a questa funzionalità.",
                // The id of the request in flight, not a new one: it used to invent an
                // AUTH_xxxxxxxx of its own, so a refused request came back carrying one id
                // in the X-Request-Id header and a different one in this field - the exact
                // failure the correlation id exists to prevent. Read from the request
                // directly because RequestContextHolder is not guaranteed to be populated
                // this early, before the dispatch.
                RequestCorrelationFilter.current(request)
        );
        EnvelopeWriter.write(response, HttpStatus.UNAUTHORIZED, body);
    }

    private static String challengeFor(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.startsWith("Bearer ")
                ? INVALID_TOKEN_CHALLENGE
                : CHALLENGE;
    }
}
