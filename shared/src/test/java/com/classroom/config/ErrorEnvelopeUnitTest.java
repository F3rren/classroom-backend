package com.classroom.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two responses a client sees before any controller: 401 when authentication is
 * missing, 403 when the permission is.
 *
 * They were covered only indirectly by the integration tests, which live in the application
 * module. Moved into shared they were left with no tests of their own, and this is the kind
 * of code that deserves a direct one: their JSON body is a contract towards the client, not
 * dettaglio interno.
 */
class ErrorEnvelopeUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void missingAuthenticationProducesA401Envelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint().commence(
                new MockHttpServletRequest(), response, new BadCredentialsException("nessun token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("userMessage").asText()).isEqualTo("Devi effettuare il login per accedere a questa funzionalità.");
        // The sessionId is there to correlate the logs. The prefix is no longer AUTH_
        // because the controller no longer invents it: it is the request's id, the same one
        // the error handler sees. Outside an HTTP request - as here - it falls back to a
        // generated one, and what matters is that the field is never empty.
        assertThat(body.get("sessionId").asText()).isNotBlank();
    }

    @Test
    void the401CarriesTheRequestsOwnIdAndNotAFreshOne() throws Exception {
        // The defect this replaced: the handler minted an AUTH_xxxxxxxx of its own, so a
        // refused request came back with one id in the X-Request-Id header and a different
        // one in the body - two ids for one request, which is the exact failure the
        // correlation id exists to prevent.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestCorrelationFilter.ATTRIBUTE, "REQ_DEADBEEF");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint().commence(
                request, response, new BadCredentialsException("nessun token"));

        assertThat(objectMapper.readTree(response.getContentAsString()).get("sessionId").asText())
                .isEqualTo("REQ_DEADBEEF");
    }

    @Test
    void the403CarriesTheRequestsOwnIdToo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestCorrelationFilter.ATTRIBUTE, "REQ_DEADBEEF");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler().handle(
                request, response, new AccessDeniedException("permesso negato"));

        assertThat(objectMapper.readTree(response.getContentAsString()).get("sessionId").asText())
                .isEqualTo("REQ_DEADBEEF");
    }

    @Test
    void a401WithoutATokenChallengesForOne() throws Exception {
        // RFC 9110 section 11.6.1 makes WWW-Authenticate mandatory on a 401: without it a
        // client is refused and not told what to attempt. It was missing entirely.
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint().commence(
                new MockHttpServletRequest(), response, new BadCredentialsException("nessun token"));

        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer realm=\"classroom\"");
    }

    @Test
    void a401OnATokenThatWasSentSaysTheTokenIsTheProblem() throws Exception {
        // RFC 6750 section 3.1. The two cases call for opposite actions - without a token you
        // log in, with an expired one you refresh and retry - and only the request says which
        // it was, since JwtAuthFilter does not throw on a bad token, it just leaves the
        // request unauthenticated. It is also the case the README warns about: after
        // JWT_SECRET changes, every existing token starts being refused "for no visible
        // reason". Now the reason is in the header.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer un-token-scaduto");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint().commence(
                request, response, new BadCredentialsException("token scaduto"));

        assertThat(response.getHeader("WWW-Authenticate"))
                .startsWith("Bearer realm=\"classroom\"")
                .contains("error=\"invalid_token\"");
    }

    @Test
    void insufficientPermissionsProducesA403Envelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler().handle(
                new MockHttpServletRequest(), response, new AccessDeniedException("permesso negato"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.get("userMessage").asText()).isEqualTo("Non hai i permessi necessari per accedere a questa risorsa.");
    }

    @Test
    void theTwoResponsesNeverLeakTheInternalException() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler().handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("dettaglio interno che non deve uscire"));

        assertThat(response.getContentAsString()).doesNotContain("dettaglio interno");
    }
}
