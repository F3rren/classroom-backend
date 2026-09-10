package com.classroom.exception;

/**
 * The caller has been refused for asking too often. Becomes a 429.
 *
 * It carries the delay because a 429 without Retry-After leaves the client guessing, and it
 * usually guesses too soon: RFC 9110 section 10.2.3 is what makes the refusal actionable
 * rather than merely correct. Whoever throws is the only one who knows when the limit lifts,
 * so the value travels with the exception instead of being invented by the handler.
 *
 * Until now the 429 was built inline in AuthController, the one place that produces one.
 * With a type, a second endpoint that ever needs limiting gets the status, the envelope and
 * the header without writing any of them again.
 */
public class TooManyRequestsException extends ApplicationException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String errorCode, String message, String userMessage,
                                    long retryAfterSeconds) {
        super(errorCode, message, userMessage);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** How long the caller should wait, in seconds, for the Retry-After header. */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
