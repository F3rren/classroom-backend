package com.classroom.exception;

/**
 * The requested resource does not exist. Becomes a 404.
 *
 * It replaces the null that the services used to return for "not found", which was
 * indistinguishable from the null they returned for "the operation failed". The worst case
 * was deleteRoom, which used false for both: a room still referenced by a booking answered
 * 404, meaning "it does not exist", about a room that existed perfectly well.
 */
public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }

    /**
     * The shortcut for the common case: an entity looked up by id.
     *
     * The id goes in the technical message only. It is useful in a log and means nothing
     * to the person reading the answer, who already knows what they asked for.
     *
     * Generic on purpose: shared cannot know the resource types of every service - booking
     * and auth do not share a domain vocabulary, only this mechanism. Each service ties its
     * own technicalName/errorCode/userMessage together with its own ResourceType enum,
     * whose notFoundById(id) is the one place that calls this and cannot mismatch them.
     * Call this directly only where there is no local ResourceType to reach for.
     */
    public static ResourceNotFoundException forId(String technicalName, String errorCode, String userMessage, Object id) {
        return new ResourceNotFoundException(
                errorCode,
                String.format("%s not found with id: %s", technicalName, id),
                userMessage);
    }
}
