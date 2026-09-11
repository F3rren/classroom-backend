package com.classroom.booking.dto;

/** The per-status booking counts, inside AdminBookingsPayload. */
public record BookingStats(long total, long active, long cancelled) {
}
