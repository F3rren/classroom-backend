package com.prenotazioni.exception;

/**
 * The base of the exceptions the domain uses to report an outcome the caller has to know.
 *
 * It exists because those outcomes used to travel as null and false: a service returned null
 * and the controller had to INFER the reason, writing by hand a message that could be wrong.
 * With a type, the reason travels with the error and GlobalExceptionHandler translates it
 * once, instead of every controller doing it its own way.
 *
 * The three fields are the ones the envelope already exposes:
 *  - errorCode: a stable code a client can branch on;
 *  - getMessage(): descrizione tecnica, finisce nei log;
 *  - userMessage: the sentence shown to whoever is using the application. Italian.
 *
 * Abstract on purpose: it is the subtype that determines the HTTP status, so throwing "a
 * generic application exception" must not be possible - it would mean not having decided what
 * kind of error this is.
 */
public abstract class ApplicationException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    protected ApplicationException(String errorCode, String message, String userMessage) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
