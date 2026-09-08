package com.classroom.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wrapper around almost every response. The error branch was already covered by
 * GlobalExceptionHandlerUnitTest; what is pinned here is the success branch and, above all,
 * the omission of null fields: @JsonInclude(NON_NULL) reproduces the behaviour of the Map.of
 * originally used, which could not hold null values. If somebody removed it, the responses
 * would gain "error": null fields the client does not expect.
 */
class ApiEnvelopeUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aSuccessCarriesTheDataAndOmitsTheErrorFields() throws Exception {
        ApiEnvelope<String> envelope = ApiEnvelope.success("Operazione riuscita", "payload", "S1234");

        assertThat(envelope.isSuccess()).isTrue();
        assertThat(envelope.getData()).isEqualTo("payload");
        assertThat(envelope.getMessage()).isEqualTo("Operazione riuscita");
        assertThat(envelope.getSessionId()).isEqualTo("S1234");
        assertThat(envelope.getError()).isNull();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(envelope));
        assertThat(json.has("error")).isFalse();
        assertThat(json.has("userMessage")).isFalse();
        assertThat(json.get("data").asText()).isEqualTo("payload");
    }

    @Test
    void timestampUsesTheFormatTheFrontendParses() {
        ApiEnvelope<Void> envelope = ApiEnvelope.success("ok", null, "S1");

        // yyyy-MM-dd HH:mm:ss, the same as util.Timestamps
        assertThat(envelope.getTimestamp()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void aSuccessWithNoDataOmitsTheFieldAltogether() throws Exception {
        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsString(ApiEnvelope.success("fatto", null, "S2")));

        assertThat(json.has("data")).isFalse();
        assertThat(json.get("success").asBoolean()).isTrue();
    }

    @Test
    void anErrorExposesExactlySixKeys() throws Exception {
        // A contract shared with the gateway. The gateway cannot reuse this class (shared
        // brings spring-boot-starter-web, which in a WebFlux application would start Tomcat),
        // so it rebuilds the envelope by hand. This test is the twin of
        // RisposteErroreTest: insieme impediscono alle due forme di divergere in silenzio.
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                ApiEnvelope.error("CODICE", "messaggio tecnico", "messaggio per l'utente", "S1")));

        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "success", "error", "message", "userMessage", "timestamp", "sessionId");
    }
}
