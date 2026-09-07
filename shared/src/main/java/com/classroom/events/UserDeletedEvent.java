package com.classroom.events;

/**
 * A user was deleted; the services holding their bookings and notifications have to remove
 * them too.
 *
 * The contract between the publisher (auth-service) and its two independent consumers
 * (booking-service, notification-service). It sits in shared for the same reason as
 * BookingCancelledEvent: the compiler guarantees every side is talking about the same thing.
 *
 * Just the id, unlike BookingCancelledEvent: there is no display text to carry, because
 * nothing here is shown to a person - each consumer only has to delete rows that belong to
 * this id.
 *
 * COMPATIBILITY: once there are messages on the queue, this type cannot change freely. Adding
 * a field is fine (a consumer on the old version ignores it); removing or renaming one is
 * not: it breaks the messages already published and not yet read.
 */
public record UserDeletedEvent(Long userId) {
}
