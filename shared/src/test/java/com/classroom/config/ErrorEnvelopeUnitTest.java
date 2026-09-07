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
    void lAutenticazioneMancanteProduceUnaBusta401() throws Exception {
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
