package com.classroom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import com.classroom.util.Timestamps;

/**
 * The generic response envelope. It reproduces exactly the shape already in use across every
 * controller (success, error, message, userMessage, timestamp, sessionId for errors; success,
 * message, data, timestamp, sessionId for successes).
 *
 * @JsonInclude(NON_NULL) means the fields left unset are absent from the JSON rather than
 * "null" - the same behaviour as the Map.of it replaced, which could not hold null values.
 *
 * It is called ApiEnvelope and not ApiResponse so as not to clash by name with the Swagger
 * annotation io.swagger.v3.oas.annotations.responses.ApiResponse: that way both can be
 * imported normally instead of having to qualify one of them inline.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Schema(description = "The envelope wrapping almost every response: it separates outcome, messages and payload")
public class ApiEnvelope<T> {

    @Schema(description = "Whether the operation succeeded", example = "true")
    private boolean success;
    @Schema(description = "A stable error code, present only on an error", example = "BOOKING_CONFLICT")
    private String error;
    @Schema(description = "The technical message, for developers", example = "Could not book the room")
    private String message;
    @Schema(description = "The message meant to be shown to the end user. Italian, unlike the rest",
            example = "L'aula non e' disponibile nel periodo richiesto.")
    private String userMessage;
    @Schema(description = "The response payload, absent on an error")
    private T data;
    @Schema(description = "The moment of the response", example = "2026-08-31 14:05:00")
    private String timestamp;
    @Schema(description = "The request id, which is what correlates the logs", example = "S4D094712")
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
     * Generic in T (rather than fixed to Void) so that a controller method can declare
     * ResponseEntity&lt;ApiEnvelope&lt;XxxPayload&gt;&gt; instead of ResponseEntity&lt;?&gt; even on its
     * error branches - Springdoc can then derive the real response schema instead of showing
     * a generic "object" (a limitation of ResponseEntity&lt;?&gt; with generics erased at
     * runtime). The caller lets T be inferred from context, typically the method's return.
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
