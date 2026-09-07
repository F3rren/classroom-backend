package com.classroom.auth.exception;

import com.classroom.exception.ResourceNotFoundException;

/**
 * The kinds of resource this service can look up by id and find missing - today, only USER.
 *
 * It used to live in shared, alongside booking-service's ROOM/BOOKING/COURSE - a single enum
 * standing in for two services' domain vocabularies, with nobody owning it. Every new
 * resource type either service added meant opening a module it did not own. This one holds
 * only what auth-service owns; a single constant is not a reason to inline the mechanism,
 * since the next one this service ever needs gets it for free.
 *
 * Same reasoning as before for why the three pieces are tied together rather than passed as
 * three loose strings at every throw site: a technical name for the log, an error code a
 * client branches on, and a sentence for the person in front of the frontend, in Italian.
 * notFoundById(id) is the one place they are composed, so a call site cannot mismatch them.
 */
public enum ResourceType {

    USER("User", "USER_NOT_FOUND", "L'utente richiesto non esiste.");

    private final String technicalName;
    private final String errorCode;
    private final String userMessage;

    ResourceType(String technicalName, String errorCode, String userMessage) {
        this.technicalName = technicalName;
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    /** The exception to throw when the entity with this id does not exist. */
    public ResourceNotFoundException notFoundById(Object id) {
        return ResourceNotFoundException.forId(technicalName, errorCode, userMessage, id);
    }
}
