package com.classroom.booking.dto;

/** The success response for DELETE /api/bookings/{id}. */
public record CancellationAckPayload(Long bookingId, Long userId, String cancelledAt) {
}
