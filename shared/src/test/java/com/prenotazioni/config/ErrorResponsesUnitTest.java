package com.prenotazioni.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le due risposte che un client vede prima di qualunque controller: 401 quando manca
 * l'autenticazione, 403 quando manca il permesso.
 *
 * Erano coperte solo di rimbalzo dai test di integrazione, che vivono nel modulo
 * applicativo. Spostate in shared restavano senza test propri, ed e' il tipo di codice
 * che merita un test diretto: il loro corpo JSON e' contratto verso il frontend, non
 * dettaglio interno.
 */
class ErrorResponsesUnitTest {

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
        // Il sessionId serve a correlare i log. Il prefisso non e' piu' AUTH_ perche' non
        // e' piu' il controller a inventarselo: e' l'id della richiesta, lo stesso che vede
        // il gestore degli errori. Fuori da una richiesta HTTP - come qui - si ripiega su
        // uno generato, e cio' che conta e' che il campo non sia mai vuoto.
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
