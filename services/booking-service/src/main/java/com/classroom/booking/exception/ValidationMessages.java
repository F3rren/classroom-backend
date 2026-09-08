package com.classroom.booking.exception;

/**
 * The sentences a rejected parameter is explained with, in the one form an annotation can
 * consume.
 *
 * They do not live in {@link ResourceType} next to the other user-facing wordings of this
 * service, and that is a language constraint rather than a choice: the value of an
 * annotation attribute has to be a compile-time constant, and an enum's fields are not one.
 * This is the same idea - one wording, one place - expressed the only way @Positive can read
 * it.
 *
 * It exists because the rule "an id is a positive number" used to be written as an if at the
 * top of every method that took one: eight copies across three controllers, and three
 * different Italian sentences for the identical rule ("...un numero positivo valido.",
 * "...un numero positivo.", "...un numero positivo maggiore di 0"). Whoever met two of them
 * had no way of telling whether the difference meant anything.
 */
public final class ValidationMessages {

    public static final String ROOM_ID_POSITIVE = "L'ID dell'aula deve essere un numero positivo.";
    public static final String BOOKING_ID_POSITIVE = "L'ID della prenotazione deve essere un numero positivo.";

    private ValidationMessages() {
    }
}
