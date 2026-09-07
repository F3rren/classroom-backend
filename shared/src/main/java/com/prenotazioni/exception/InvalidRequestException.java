package com.prenotazioni.exception;

/**
 * The request carries a value that is not acceptable. Becomes a 400.
 *
 * It is for the cases Bean Validation does not cover because they depend on the domain: an
 * enum value that does not exist, arriving in a path variable, for instance. Use it sparingly
 * - if a rule can be expressed as an annotation on a DTO, that is the right home for it,
 * because it then also reaches the OpenAPI schema.
 *
 * It is NOT an alias for IllegalArgumentException. That one signals a programming error and
 * rightly ends up as a 500: mapping it to 400 would hide the server's bugs by making them
 * look like the caller's fault.
 */
public class InvalidRequestException extends ApplicationException {

    public InvalidRequestException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }
}
