package com.classroom.exception;

/**
 * A downstream service did not answer, and the operation needs repeating.
 *
 * It becomes a 503 and not a 500, and that distinction is for whoever reads the response:
 * un 500 dice "e' rotto qualcosa", un 503 dice "riprova". Sono due azioni diverse.
 *
 * The concrete case it was born for: deleting a user deletes their data in the other
 * services first and the user only afterwards. If one of those services does not answer the
 * operation stops halfway - and the only thing that finishes it is somebody repeating it. As
 * long as that failure arrived as "internal server error", repeating was not the obvious
 * conclusion, and the half that was done stayed done.
 *
 * Use it ONLY when repeating genuinely makes sense: an unreachable service, a timeout, a 5xx
 * downstream. A downstream refusal - a 400, a 404 - is not this: repeating would give the same
 * outcome, and inviting somebody to do it would be a lie.
 */
public class ServiceUnavailableException extends ApplicationException {

    public ServiceUnavailableException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }
}
