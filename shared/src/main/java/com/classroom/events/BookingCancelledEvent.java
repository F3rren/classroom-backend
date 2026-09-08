package com.classroom.events;

/**
 * A booking was cancelled by an administrator.
 *
 * The contract between the publisher (the booking service) and the consumer (the
 * notification service). It sits in shared on purpose: that way the compiler guarantees the
 * two sides are talking about the same thing, instead of relying on two maps of strings that
 * can drift apart with nothing to signal it.
 *
 * The fields are already formatted for display (dates and times as strings) because a
 * notification is a message to be read, not data to compute on: the receiver has to reformat
 * nothing, and the format stays decided by the side that knows the request's time zone.
 *
 * It is a record, and so immutable: a message travelling between services has no reason to
 * be modifiable after it has been built.
 *
 * COMPATIBILITY: once there are messages on the queue, this type cannot change freely.
 * Adding a field is fine (a consumer on the old version ignores it); removing or renaming one
 * is not: it breaks the messages already published and not yet read.
 */
public record BookingCancelledEvent(
        Long userId,
        Long bookingId,
        String roomName,
        String adminName,
        String bookingDate,
        String startTime,
        String endTime,
        String reason) {
}
