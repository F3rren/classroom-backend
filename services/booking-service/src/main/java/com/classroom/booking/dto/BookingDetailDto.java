package com.classroom.booking.dto;

import com.classroom.booking.model.BookingStatus;

import java.time.LocalDateTime;

/**
 * The "full detail" view of a booking (joining room, user and course), populated straight
 * from the JPQL "new" query in {@code BookingRepository} rather than from a generic Map.
 *
 * THE FIELD ORDER IS PART OF THE CONTRACT: it has to match the order of the columns in the
 * SELECT new exactly, because a record's canonical constructor follows declaration order.
 * Renaming a field is safe; moving one silently shifts every value after it into the wrong
 * slot, and the types line up often enough that the compiler will not always object.
 *
 * courseId, courseName and teacher are null for bookings with no course attached, which is
 * the case for admin blocks and maintenance.
 */
public record BookingDetailDto(
        Long bookingId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BookingStatus status,
        String bookingNotes,
        LocalDateTime createdAt,
        Long roomId,
        String roomName,
        Integer roomCapacity,
        Integer roomFloor,
        Long userId,
        String username,
        Long courseId,
        String courseName,
        String teacher,
        String timeStatus) {
}
