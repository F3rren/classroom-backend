package com.prenotazioni.exception;

/**
 * The conflict specific to bookings: the time slot is already taken.
 *
 * It stays a type of its own even though it adds no fields to DomainConflictException,
 * because BookingController throws it explicitly when translating a violation of the
 * anti-overlap constraint, and it is that name that makes the place it happens readable.
 * By inheriting, it no longer duplicates errorCode and userMessage.
 */
public class BookingConflictException extends DomainConflictException {

    public BookingConflictException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }
}
