package com.prenotazioni.exception;

/**
 * The kinds of resource that can be looked up by id and turn out not to exist.
 *
 * It exists because a "not found" carries three things that have to agree with each other
 * and are written in two different languages:
 *
 *  - a technical name, English, for the log line and the exception message;
 *  - an error code, which is what a client branches on;
 *  - a sentence for the person in front of the frontend, which stays Italian.
 *
 * Passing those as three loose strings at every throw site is how they drift apart: the
 * code says ROOM_NOT_FOUND while the sentence talks about a booking, and nothing catches
 * it because both are just strings. Tying them together here means a call site names the
 * resource once and cannot mismatch them.
 *
 * The Italian sentences are written out in full rather than composed from a noun, because
 * composing them does not survive translation: "L'aula richiesta" and "Il corso richiesto"
 * do not share a template, and the version that tried produced "Aula richiesta non esiste."
 */
public enum ResourceType {

    ROOM("Room", "ROOM_NOT_FOUND", "L'aula richiesta non esiste."),
    BOOKING("Booking", "BOOKING_NOT_FOUND", "La prenotazione richiesta non esiste."),
    COURSE("Course", "COURSE_NOT_FOUND", "Il corso richiesto non esiste."),
    USER("User", "USER_NOT_FOUND", "L'utente richiesto non esiste.");

    private final String technicalName;
    private final String errorCode;
    private final String userMessage;

    ResourceType(String technicalName, String errorCode, String userMessage) {
        this.technicalName = technicalName;
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    /** English, for the exception message and the logs. */
    public String getTechnicalName() {
        return technicalName;
    }

    /** What a client branches on. */
    public String getErrorCode() {
        return errorCode;
    }

    /** Italian, because this one is read by a person and not by a programmer. */
    public String getUserMessage() {
        return userMessage;
    }
}
