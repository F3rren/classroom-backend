package com.prenotazioni.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'involucro di quasi tutte le risposte. Il ramo di errore era gia' coperto da
 * GlobalExceptionHandlerUnitTest; qui si fissa il ramo di successo e, soprattutto,
 * l'omissione dei campi nulli: @JsonInclude(NON_NULL) riproduce il comportamento del
 * Map.of usato in origine, che non poteva contenere valori null. Se qualcuno lo
 * togliesse, le risposte guadagnerebbero campi "error": null che il frontend non si aspetta.
 */
class ApiEnvelopeUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successCarriesThePayloadAndOmitsTheErrorFields() throws Exception {
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

        // yyyy-MM-dd HH:mm:ss, lo stesso di util.Timestamps
        assertThat(envelope.getTimestamp()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void aSuccessWithoutDataOmitsTheFieldEntirely() throws Exception {
        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsString(ApiEnvelope.success("fatto", null, "S2")));

        assertThat(json.has("data")).isFalse();
        assertThat(json.get("success").asBoolean()).isTrue();
    }
}
