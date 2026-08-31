package com.prenotazioni.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
public class ApiEnvelope<T> {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private boolean success;
    private String error;
    private String message;
    private String userMessage;
    private T data;
    private String timestamp;
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
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    public boolean isSuccess() { return success; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getUserMessage() { return userMessage; }
    public T getData() { return data; }
    public String getTimestamp() { return timestamp; }
    public String getSessionId() { return sessionId; }
}
