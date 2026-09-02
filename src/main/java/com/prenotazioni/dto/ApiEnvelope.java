package com.prenotazioni.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import com.prenotazioni.util.Timestamps;

import java.time.LocalDateTime;

/**
 * Envelope generico di risposta, riproduce esattamente lo shape gia' in uso
 * in tutti i controller (Map.of(success,error,message,userMessage,timestamp,sessionId)
 * per gli errori, Map.of(success,message,data,timestamp,sessionId) per i successi).
 * @JsonInclude(NON_NULL) fa si' che i campi non impostati siano assenti dal JSON,
 * non "null" - stesso comportamento di Map.of che non puo' contenere valori null.
 * Chiamata ApiEnvelope (non ApiResponse) per non entrare in conflitto di nome con
 * l'annotazione Swagger io.swagger.v3.oas.annotations.responses.ApiResponse, cosi'
 * entrambe si possono importare normalmente invece di doverne qualificare una inline.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Schema(description = "Involucro comune a quasi tutte le risposte: distingue esito, messaggi e payload")
public class ApiEnvelope<T> {

    @Schema(description = "true se l'operazione e' riuscita", example = "true")
    private boolean success;
    @Schema(description = "Codice di errore stabile, presente solo in caso di errore", example = "BOOKING_CONFLICT")
    private String error;
    @Schema(description = "Messaggio tecnico per gli sviluppatori", example = "Impossibile prenotare l'aula")
    private String message;
    @Schema(description = "Messaggio pensato per essere mostrato all'utente finale",
            example = "L'aula non e' disponibile nel periodo richiesto.")
    private String userMessage;
    @Schema(description = "Payload della risposta, assente in caso di errore")
    private T data;
    @Schema(description = "Momento della risposta", example = "2026-08-31 14:05:00")
    private String timestamp;
    @Schema(description = "Identificativo della richiesta, utile per correlare i log", example = "S4D094712")
    private String sessionId;

    public static <T> ApiEnvelope<T> success(String message, T data, String sessionId) {
        ApiEnvelope<T> response = new ApiEnvelope<>();
        response.success = true;
        response.message = message;
        response.data = data;
        response.timestamp = now();
        response.sessionId = sessionId;
        return response;
    }

    /**
     * Generico su T (invece di fisso su Void) cosi' un metodo controller puo' dichiarare
     * ResponseEntity&lt;ApiEnvelope&lt;XxxPayload&gt;&gt; invece di ResponseEntity&lt;?&gt; anche nei
     * rami di errore - Springdoc puo' allora derivare lo schema di risposta reale invece
     * di mostrare "object" generico (limite di ResponseEntity&lt;?&gt; con i generici cancellati
     * a runtime). Il chiamante lascia inferire T dal contesto (es. dal return del metodo).
     */
    public static <T> ApiEnvelope<T> error(String errorCode, String message, String userMessage, String sessionId) {
        ApiEnvelope<T> response = new ApiEnvelope<>();
        response.success = false;
        response.error = errorCode;
        response.message = message;
        response.userMessage = userMessage;
        response.timestamp = now();
        response.sessionId = sessionId;
        return response;
    }

    private static String now() {
        return Timestamps.now();
    }
}
