package com.classroom.exception;

import com.classroom.dto.ApiEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * The errors that belong to the HTTP contract rather than to the domain: a method that does
 * not exist on that path, a body Jackson cannot read, a media type nobody negotiated.
 *
 * They exist because these cases used to be answered with 500 INTERNAL_ERROR. Spring raises
 * about twenty different exceptions for them and each already carries the right status;
 * GlobalExceptionHandler's @ExceptionHandler(Exception.class), being consulted before
 * Spring's own resolver, was flattening them all onto the one status that means "nobody
 * knows what happened". Measured before the change: a malformed body, a non-numeric path
 * variable, a missing query parameter, a wrong method and a wrong Content-Type all answered
 * 500, each with a stack trace at ERROR.
 *
 * KEYED BY STATUS AND NOT BY EXCEPTION TYPE, on purpose. It is
 * ResponseEntityExceptionHandler that decides which status each exception deserves, and it
 * already does it correctly; what was missing was only the envelope to say it in. Keying on
 * the status means a Spring version that adds a twenty-first exception is covered the day it
 * appears, instead of falling into the 500 branch with nobody noticing.
 *
 * Same reasoning as ResourceType for why the three pieces travel together rather than as
 * three loose strings: a stable code a client can branch on, a technical message for the
 * log, and one sentence in Italian for whoever is in front of the screen. toEnvelope() is
 * the one place they are composed, so a call site cannot mismatch them.
 */
public enum ProtocolError {

    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
            "Malformed or incomplete request",
            "La richiesta non e' nel formato previsto."),

    /**
     * The values are the ones the two hand-written 404 handlers used before they became
     * overrides, character for character: a path that does not exist has always answered
     * this, and a client already branching on NOT_FOUND must not notice the change.
     */
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND",
            "Resource not found",
            "L'indirizzo richiesto non esiste."),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
            "HTTP method not supported on this path",
            "Questa operazione non e' disponibile su questo indirizzo."),

    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
            "No representation acceptable to the client",
            "Il formato di risposta richiesto non e' disponibile."),

    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
            "Request body above the accepted size",
            "I dati inviati sono troppo grandi."),

    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
            "Content-Type not supported",
            "Il formato dei dati inviati non e' supportato."),

    /**
     * Raised by an async request that ran out of time. Same wording as the gateway's 503,
     * because it suggests the same action: this one is worth repeating, a 500 is not.
     */
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
            "The request could not be completed in time",
            "Il servizio non e' momentaneamente disponibile. Riprova fra qualche istante."),

    /**
     * The fallback, and also what handleGeneric answers with. The three values are the ones
     * that handler already used, so the response to an unexpected failure is unchanged.
     */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "Unhandled internal error",
            "Si e' verificato un errore imprevisto. Se il problema persiste, contatta il supporto tecnico.");

    private final HttpStatus status;
    private final String code;
    private final String message;
    private final String userMessage;

    ProtocolError(HttpStatus status, String code, String message, String userMessage) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.userMessage = userMessage;
    }

    /**
     * The entry for a status, or the closest honest thing to it.
     *
     * The fallback is reachable: ErrorResponseException carries a status of its own
     * choosing, so a code not listed above can arrive. A 4xx is still the caller's problem
     * and a 5xx still ours, and answering on the wrong side of that line would mislead more
     * than a generic code does. The status itself is never altered - the caller passes it
     * through untouched, so the response still says 418 even when the code says BAD_REQUEST.
     *
     * Compared by value() and not by equals(): a status outside the HttpStatus enum arrives
     * as a DefaultHttpStatusCode, which is never equal to an HttpStatus constant.
     */
    public static ProtocolError of(HttpStatusCode status) {
        for (ProtocolError error : values()) {
            if (error.status.value() == status.value()) {
                return error;
            }
        }
        return status.is5xxServerError() ? INTERNAL_ERROR : BAD_REQUEST;
    }

    /** The envelope to answer with: the one place the three pieces are composed. */
    public <T> ApiEnvelope<T> toEnvelope(String sessionId) {
        return ApiEnvelope.error(code, message, userMessage, sessionId);
    }

    /** The stable code a client branches on. */
    public String code() {
        return code;
    }

    /** The technical message, the one that goes in the envelope and in the log. */
    public String message() {
        return message;
    }

    /** The sentence shown to whoever is using the application. Italian, like every other. */
    public String userMessage() {
        return userMessage;
    }
}
