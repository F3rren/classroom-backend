package com.classroom.exception;

/**
 * The operation conflicts with the current state of the data. Becomes a 409.
 *
 * This is the case of a room name already taken, which used to be a null the controller
 * presented as a 400: a state the caller cannot fix by rephrasing the request is not a syntax
 * error, and 409 says so where 400 does not.
 */
public class DomainConflictException extends ApplicationException {

    public DomainConflictException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }
}
